package top.s1metro.s1mtr.service;

import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;

/**
 * 侧线限速的读写工具。
 * <p>
 * 侧线限速以 styles 条目 {@code s1mtr:sidingSpeedLimit=<km/h>} 持久化在轨道上：
 * <ul>
 *   <li>不修改轨道自身的限速(speedLimit1 / speedLimit2),因此是"运行期限速",随时可以清除;</li>
 *   <li>随轨道一起被 MTR 的数据包保存与同步,无需额外的配置文件。</li>
 * </ul>
 * 限速语义:列车在该侧线上行驶时,实际速度目标取 {@code min(轨道限速, 侧线限速)}。
 * 例如侧线限速 80,经过限速 60 的轨道仍按 60 行驶,进入限速 100 的轨道则按 80 行驶。
 */
public final class SidingSpeedLimit {

	/** styles 中保存侧线限速的键前缀。 */
	public static final String STYLE_PREFIX = "s1mtr:sidingSpeedLimit=";

	/** 侧线限速上限(km/h),避免玩家填入离谱数值。 */
	public static final long MAX_LIMIT = 500L;

	private SidingSpeedLimit() {
	}

	/**
	 * 读取轨道上保存的侧线限速(km/h)。
	 *
	 * @param rail 轨道
	 * @return 侧线限速(km/h);未设置或非法时返回 0
	 */
	public static long get(Rail rail) {
		if (rail == null) {
			return 0L;
		}
		final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
		for (String style : accessor.s1mtr$getStyles()) {
			if (style.startsWith(STYLE_PREFIX)) {
				try {
					final long value = Long.parseLong(style.substring(STYLE_PREFIX.length()));
					return value > 0 ? Math.min(value, MAX_LIMIT) : 0L;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return 0L;
	}

	/**
	 * 复制轨道并写入(或清除)侧线限速。
	 * <p>
	 * 轨道自身的几何参数与限速保持不变,只有 styles 被替换。
	 *
	 * @param rail     原轨道
	 * @param limitKmh 侧线限速(km/h);&lt;= 0 表示清除该设置
	 * @return 写入后的新轨道
	 */
	public static Rail withLimit(Rail rail, long limitKmh) {
		if (rail == null) {
			return null;
		}
		final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
		final ObjectArrayList<String> styles = new ObjectArrayList<>(accessor.s1mtr$getStyles());
		for (int i = styles.size() - 1; i >= 0; i--) {
			if (styles.get(i).startsWith(STYLE_PREFIX)) {
				styles.remove(i);
			}
		}
		if (limitKmh > 0) {
			styles.add(STYLE_PREFIX + Math.min(limitKmh, MAX_LIMIT));
		}
		return Rail.copy(rail, styles);
	}
}
