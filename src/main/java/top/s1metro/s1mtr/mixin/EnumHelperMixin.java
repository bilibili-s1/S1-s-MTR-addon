package top.s1metro.s1mtr.mixin;

import top.s1metro.s1mtr.service.AngleExtra;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.EnumHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 让 MTR core 的枚举反序列化能恢复「幻影角度」实例。
 * <p>
 * 万向节点建出的任意角度轨道，其 {@code angle1/angle2} 是 {@link AngleMixin} 创建的幻影实例，
 * 名字形如 {@code "D123.456"} / {@code "R2.153"}。core 生成类写入时正常写出，读取走
 * {@link EnumHelper#valueOf}，按 {@code Enum.valueOf} 找不到常量会静默回退默认值，
 * 导致服务端重启 / 跨端同步后任意角度丢失。本 mixin 覆写 {@code valueOf} 解析 D/R 前缀恢复。
 *
 * <p>移植自 FangSu For MTR 4（MIT）。
 */
@Mixin(value = EnumHelper.class, remap = false)
public interface EnumHelperMixin {

	@SuppressWarnings("unchecked")
	@Overwrite
	static <T extends Enum<T>> T valueOf(T defaultValue, String name) {
		try {
			return Enum.valueOf(defaultValue.getDeclaringClass(), name);
		} catch (Exception e) {
			if (defaultValue != null && defaultValue.getDeclaringClass() == Angle.class && name != null) {
				final String trimmed = name.trim();
				if (trimmed.length() > 1) {
					final char prefix = trimmed.charAt(0);
					if (prefix == 'D' || prefix == 'R') {
						try {
							final double value = Double.parseDouble(trimmed.substring(1));
							return (T) (prefix == 'D'
									? AngleExtra.fromDegrees(value)
									: AngleExtra.fromRadians(value));
						} catch (NumberFormatException ignored) {
						}
					}
				}
			}
			return defaultValue;
		}
	}
}
