package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;

import java.util.function.Consumer;

/**
 * 层参数编辑界面（方速混合构建器的编辑方式）：起点、间隔、厚度、延伸方向、替换模式。
 * <p>
 * 这些参数保存在 {@link CompositeLayerSchedule.Entry} 里，随预设字符串一起序列化，
 * 因此与现有 s1mtr 预设体系互通（旧预设缺这些字段时按默认值读取）。
 */
public class LayerParamsScreen extends MTRScreenBase {

	private static final int PANEL_W = 320;
	private static final int PANEL_H = 196;

	private static CompositeLayerSchedule s1mtr$schedule;
	private static int s1mtr$index;
	private static Consumer<CompositeLayerSchedule> s1mtr$callback;

	private CompositeLayerSchedule schedule;
	private final int index;
	private TextFieldWidgetExtension startField;
	private TextFieldWidgetExtension intervalField;
	private TextFieldWidgetExtension thicknessField;
	private boolean thickDir;
	private boolean replacement;

	public static void open(CompositeLayerSchedule schedule, int index, Consumer<CompositeLayerSchedule> onDone) {
		s1mtr$schedule = schedule.copy();
		s1mtr$index = index;
		s1mtr$callback = onDone;
		MinecraftClient.getInstance().setScreen(new LayerParamsScreen());
	}

	public LayerParamsScreen() {
		super();
		if (s1mtr$schedule == null) {
			s1mtr$schedule = new CompositeLayerSchedule();
		}
		this.schedule = s1mtr$schedule;
		this.index = Math.min(Math.max(s1mtr$index, 0), schedule.size() - 1);
		final CompositeLayerSchedule.Entry e = schedule.get(this.index);
		this.thickDir = e.thickDir;
		this.replacement = e.replacement;
	}

	@Override
	protected void init2() {
		super.init2();
		final int px = (width - PANEL_W) / 2;
		final int py = (height - PANEL_H) / 2;
		final CompositeLayerSchedule.Entry e = schedule.get(index);

		startField = newField(px + 150, py + 34, String.valueOf(e.start));
		intervalField = newField(px + 150, py + 60, String.valueOf(e.interval));
		thicknessField = newField(px + 150, py + 86, String.valueOf(e.thickness));

		addChild(new ClickableWidget(new ButtonWidgetExtension(
				px + 20, py + 112, PANEL_W - 40, 20,
				TextHelper.literal((thickDir ? "\u00D7 " : "\u00B7 ") + "厚度方向: "
						+ (thickDir ? "向里 (+t)" : "向外 (-t)")),
				button -> {
					thickDir = !thickDir;
					commit();
					MinecraftClient.getInstance().setScreen(new LayerParamsScreen());
				})));

		addChild(new ClickableWidget(new ButtonWidgetExtension(
				px + 20, py + 138, PANEL_W - 40, 20,
				TextHelper.literal((replacement ? "\u2713 " : "\u25CB ") + "替换模式(先清除原有方块)"),
				button -> {
					replacement = !replacement;
					commit();
					MinecraftClient.getInstance().setScreen(new LayerParamsScreen());
				})));

		addChild(new ClickableWidget(new ButtonWidgetExtension(
				px + 20, py + PANEL_H - 28, PANEL_W - 40, 20,
				TextHelper.translatable("gui.done"), button -> {
					commit();
					final Consumer<CompositeLayerSchedule> cb = s1mtr$callback;
					s1mtr$callback = null;
					s1mtr$schedule = null;
					if (cb != null) {
						cb.accept(schedule);
					} else {
						MinecraftClient.getInstance().setScreen(null);
					}
				})));
	}

	private TextFieldWidgetExtension newField(int x, int y, String initial) {
		final TextFieldWidgetExtension field = new TextFieldWidgetExtension(
				x, y, 140, 20, 16, org.mtr.mapping.tool.TextCase.DEFAULT, "[^0-9.\\-]", initial);
		addChild(new ClickableWidget(field));
		return field;
	}

	private void commit() {
		final CompositeLayerSchedule.Entry e = schedule.get(index);
		e.start = parseDouble(startField, e.start);
		e.interval = Math.max(0, parseDouble(intervalField, e.interval));
		e.thickness = Math.max(1, (int) parseDouble(thicknessField, e.thickness));
		e.thickDir = thickDir;
		e.replacement = replacement;
		s1mtr$schedule = schedule.copy();
	}

	private static double parseDouble(TextFieldWidgetExtension field, double fallback) {
		if (field == null) {
			return fallback;
		}
		try {
			return Double.parseDouble(field.getText2().trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);
		final int px = (width - PANEL_W) / 2;
		final int py = (height - PANEL_H) / 2;
		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(px, py, px + PANEL_W, py + PANEL_H, 0xFE202028);
		guiDrawing.drawRectangle(px, py, px + PANEL_W, py + 1, 0xFFFFFFFF);
		guiDrawing.finishDrawingRectangle();

		graphicsHolder.drawCenteredText(TextHelper.literal("层参数 (剖面 " + (index + 1) + ")"), width / 2, py + 12, 0xFFFFFF);
		graphicsHolder.drawText(TextHelper.literal("起点(格):"), px + 20, py + 38, -1, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(TextHelper.literal("间隔(格):"), px + 20, py + 64, -1, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(TextHelper.literal("厚度(组):"), px + 20, py + 90, -1, false, GraphicsHolder.getDefaultLight());
		super.render(graphicsHolder, mouseX, mouseY, delta);
	}
}
