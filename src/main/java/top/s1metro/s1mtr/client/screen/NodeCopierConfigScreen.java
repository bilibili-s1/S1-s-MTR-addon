package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.item.ItemNodeCopier;

/**
 * 轨道节点复制工具配置界面(Shift+右键打开)。
 * <p>
 * 可勾选"自动切换速度":粘贴连接轨道时,用推荐限速(含曲率/坡度)计算并修改轨道速度,
 * 最高不超过配置的自动最高速度。
 */
public class NodeCopierConfigScreen extends MTRScreenBase {

	private static final int PANEL_W = 280;
	private static final int PANEL_H = 120;

	private final ItemStack stack;

	public NodeCopierConfigScreen(net.minecraft.item.ItemStack stack) {
		super();
		this.stack = stack;
	}

	@Override
	protected void init2() {
		super.init2();
		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;

		final boolean autoSpeed = ItemNodeCopier.isAutoSpeed(stack);
		final ButtonWidgetExtension autoSpeedButton = new ButtonWidgetExtension(
				panelX + 20, panelY + 30, PANEL_W - 40, 20,
				TextHelper.literal((autoSpeed ? "\u2713 " : "\u25CB ")
						+ TextHelper.translatable("gui.s1mtr.node_copier.auto_speed").getString()),
				btn -> {
					ItemNodeCopier.setAutoSpeed(stack, !ItemNodeCopier.isAutoSpeed(stack));
					MinecraftClient.getInstance().setScreen(new NodeCopierConfigScreen(stack));
				});
		addChild(new ClickableWidget(autoSpeedButton));

		final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(
				panelX + 20, panelY + PANEL_H - 30, PANEL_W - 40, 20,
				TextHelper.translatable("gui.done"), btn -> MinecraftClient.getInstance().setScreen(null));
		addChild(new ClickableWidget(doneButton));
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);
		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;

		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFE202028);
		guiDrawing.drawRectangle(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFFFFFFFF);
		guiDrawing.drawRectangle(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, 0xFF404050);
		guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY + PANEL_H, 0xFF808090);
		guiDrawing.drawRectangle(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF808090);
		guiDrawing.finishDrawingRectangle();

		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.node_copier.config_title"), width / 2, panelY + 8, 0xFFFFFF);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}
}
