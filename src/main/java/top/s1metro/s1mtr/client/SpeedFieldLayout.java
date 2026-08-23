package top.s1metro.s1mtr.client;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;

/**
 * UI 错开检测工具。
 * <p>
 * 在 {@code RailModifierScreen} 中添加速度输入框时，检测是否与其他模组注入的 widget 位置重叠。
 * 若重叠则自动向下移动，直到找到无冲突的位置。
 * <p>
 * Minecraft 1.20.1 的 {@link ClickableWidget} 没有公开的 {@code getY()} 方法（1.20.2 才添加），
 * 因此使用反射读取 {@code y} 和 {@code height} 字段。反射失败时安全回退到默认位置。
 */
public final class SpeedFieldLayout {

	private static final Field Y_FIELD;
	private static final Field HEIGHT_FIELD;

	static {
		Field yField = null;
		Field heightField = null;
		try {
			yField = ClickableWidget.class.getDeclaredField("y");
			yField.setAccessible(true);
			heightField = ClickableWidget.class.getDeclaredField("height");
			heightField.setAccessible(true);
		} catch (NoSuchFieldException ignored) {
			// 反射失败，findFreeY 会回退到 startY
		}
		Y_FIELD = yField;
		HEIGHT_FIELD = heightField;
	}

	private SpeedFieldLayout() {
	}

	/**
	 * 从 {@code startY} 起逐行下移，直到与现有 widget 不重叠。
	 *
	 * @param screen    当前屏幕
	 * @param ownWidget 自己的 widget（跳过自身，按引用比较）
	 * @param startY    起始 y 坐标
	 * @param height    widget 高度
	 * @return 无冲突的 y 坐标，反射失败时返回 {@code startY}
	 */
	public static int findFreeY(Screen screen, Object ownWidget, int startY, int height) {
		if (Y_FIELD == null) {
			return startY;
		}
		try {
			List<? extends Element> children = screen.children();
			int y = startY;
			boolean moved = true;
			while (moved) {
				moved = false;
				for (Element element : children) {
					if (!(element instanceof ClickableWidget widget)) {
						continue;
					}
					if (widget == ownWidget) {
						continue;
					}
					int widgetY = Y_FIELD.getInt(widget);
					int widgetHeight = HEIGHT_FIELD != null ? HEIGHT_FIELD.getInt(widget) : 20;
					// 区间相交检测
					if (widgetY < y + height && widgetY + widgetHeight > y) {
						y += 22;
						moved = true;
						break;
					}
				}
			}
			return y;
		} catch (IllegalAccessException ignored) {
			return startY;
		}
	}
}
