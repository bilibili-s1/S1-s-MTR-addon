package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.service.S1mtrConfig;

/**
 * 模组配置界面(由 Mod Menu 打开,也可通过 config/s1mtr/config.json 手改)。
 */
public class S1mtrConfigScreen extends MTRScreenBase {

	private final MinecraftClient clientRef = MinecraftClient.getInstance();
	private final Screen parent;

	private TextFieldWidgetExtension maxSpeedField;
	private TextFieldWidgetExtension perTickField;
	private ButtonWidgetExtension saveButton;

	// 标签矩形(用于鼠标悬浮显示描述)
	private int maxSpeedLabelX, maxSpeedLabelY, maxSpeedLabelW, maxSpeedLabelH;
	private int perTickLabelX, perTickLabelY, perTickLabelW, perTickLabelH;
	private MutableText hoveredTooltip = null;

	public S1mtrConfigScreen(Screen parent) {
		super();
		this.parent = parent;
	}

	@Override
	protected void init2() {
		super.init2();

		final int panelW = 320;
		final int panelH = 220;
		final int panelX = (width - panelW) / 2;
		final int panelY = (height - panelH) / 2;

		maxSpeedField = new TextFieldWidgetExtension(
				panelX + 180, panelY + 50, 100, 18, 4, TextCase.DEFAULT, "[^\\d]", "");
		maxSpeedField.setText2(String.valueOf(S1mtrConfig.autoConnectorMaxSpeed()));
		addChild(new ClickableWidget(maxSpeedField));

		perTickField = new TextFieldWidgetExtension(
				panelX + 180, panelY + 90, 100, 18, 4, TextCase.DEFAULT, "[^\\d]", "");
		perTickField.setText2(String.valueOf(S1mtrConfig.placementPerTick()));
		addChild(new ClickableWidget(perTickField));

		saveButton = new ButtonWidgetExtension(panelX + panelW / 2 - 70, panelY + panelH - 44, 140, 20,
				TextHelper.translatable("gui.s1mtr.config.save"), button -> saveConfig());
		addChild(new ClickableWidget(saveButton));

		maxSpeedLabelX = panelX + 24;
		maxSpeedLabelY = panelY + 50;
		maxSpeedLabelW = 150;
		maxSpeedLabelH = 18;

		perTickLabelX = panelX + 24;
		perTickLabelY = panelY + 90;
		perTickLabelW = 150;
		perTickLabelH = 18;
	}

	private void saveConfig() {
		final int maxSpeed = clamp(parseInt(maxSpeedField.getText2()), 1, 1000, S1mtrConfig.autoConnectorMaxSpeed());
		final int perTick = clamp(parseInt(perTickField.getText2()), 1, 1024, S1mtrConfig.placementPerTick());
		S1mtrConfig.get().autoConnectorMaxSpeed = maxSpeed;
		S1mtrConfig.get().placementPerTick = perTick;
		S1mtrConfig.save();
		clientRef.setScreen(parent);
	}

	private static int parseInt(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (Exception e) {
			return -1;
		}
	}

	private static int clamp(int v, int min, int max, int fallback) {
		if (v < min || v > max) {
			return fallback;
		}
		return v;
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		final int panelW = 320;
		final int panelH = 220;
		final int panelX = (width - panelW) / 2;
		final int panelY = (height - panelH) / 2;
		final int panelX2 = panelX + panelW;
		final int panelY2 = panelY + panelH;

		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(panelX, panelY, panelX2, panelY2, 0xFE202028);
		guiDrawing.drawRectangle(panelX, panelY, panelX2, panelY + 1, 0xFFFFFFFF);
		guiDrawing.drawRectangle(panelX, panelY2 - 1, panelX2, panelY2, 0xFF404050);
		guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY2, 0xFF808090);
		guiDrawing.drawRectangle(panelX2 - 1, panelY, panelX2, panelY2, 0xFF808090);
		guiDrawing.finishDrawingRectangle();

		final MutableText title = TextHelper.translatable("gui.s1mtr.config.title");
		graphicsHolder.drawCenteredText(title, width / 2, panelY + 16, 0xFFFFFF);

		graphicsHolder.drawText(TextHelper.translatable("gui.s1mtr.config.auto_max_speed"),
				maxSpeedLabelX, maxSpeedLabelY + 5, -1, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawText(TextHelper.translatable("gui.s1mtr.config.placement_per_tick"),
				perTickLabelX, perTickLabelY + 5, -1, false, GraphicsHolder.getDefaultLight());

		// 鼠标悬浮在标签上时记录描述,稍后绘制 tooltip
		hoveredTooltip = null;
		if (inRect(mouseX, mouseY, maxSpeedLabelX, maxSpeedLabelY, maxSpeedLabelW, maxSpeedLabelH)) {
			hoveredTooltip = TextHelper.translatable("gui.s1mtr.config.auto_max_speed.tooltip");
		} else if (inRect(mouseX, mouseY, perTickLabelX, perTickLabelY, perTickLabelW, perTickLabelH)) {
			hoveredTooltip = TextHelper.translatable("gui.s1mtr.config.placement_per_tick.tooltip");
		}

		super.render(graphicsHolder, mouseX, mouseY, delta);

		if (hoveredTooltip != null) {
			drawTooltip(graphicsHolder, hoveredTooltip, mouseX, mouseY);
		}
	}

	/** 自绘一个简单的 tooltip 框(避免依赖 MTR 内部 tooltip 工具类)。 */
	private void drawTooltip(GraphicsHolder graphicsHolder, MutableText text, int mouseX, int mouseY) {
		final String raw = text.getString();
		final int padding = 4;
		final int lineHeight = 12;
		final int maxWidth = 240;
		final int textWidth = Math.min(maxWidth, raw.length() * 7 + padding * 2);
		final int boxX = Math.min(mouseX + 12, width - textWidth - 4);
		final int boxY = Math.min(mouseY + 12, height - lineHeight - padding * 2 - 4);

		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(boxX, boxY, boxX + textWidth, boxY + lineHeight + padding * 2, 0xF0202028);
		guiDrawing.drawRectangle(boxX, boxY, boxX + textWidth, boxY + 1, 0xFF808090);
		guiDrawing.drawRectangle(boxX, boxY + lineHeight + padding * 2 - 1, boxX + textWidth, boxY + lineHeight + padding * 2, 0xFF808090);
		guiDrawing.finishDrawingRectangle();

		graphicsHolder.drawText(text, boxX + padding, boxY + padding, 0xFFFFFF, false, GraphicsHolder.getDefaultLight());
	}

	private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}
}
