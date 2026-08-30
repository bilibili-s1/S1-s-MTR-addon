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
 * 切换复制模式:
 * <ul>
 *   <li>连接模式(默认):粘贴时放置新节点并连接已有节点。</li>
 *   <li>完全复制:按相对位置复制整组节点,快速建设多线铁路。</li>
 * </ul>
 */
public class NodeCopierConfigScreen extends MTRScreenBase {

	private static final int PANEL_W = 280;
	private static final int PANEL_H = 120;

	private final ItemStack stack;
	private final boolean offHand;

	public NodeCopierConfigScreen(net.minecraft.item.ItemStack stack, boolean offHand) {
		super();
		this.stack = stack;
		this.offHand = offHand;
	}

	@Override
	protected void init2() {
		super.init2();
		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;

		final int mode = ItemNodeCopier.getMode(stack);

		final ButtonWidgetExtension connectModeButton = new ButtonWidgetExtension(
				panelX + 20, panelY + 30, PANEL_W - 40, 20,
				TextHelper.literal((mode == ItemNodeCopier.MODE_CONNECT ? "\u2713 " : "\u25CB ")
						+ TextHelper.translatable("gui.s1mtr.node_copier.mode_connect").getString()),
				btn -> {
					ItemNodeCopier.setMode(stack, ItemNodeCopier.MODE_CONNECT);
					sendModeToServer(ItemNodeCopier.MODE_CONNECT);
					MinecraftClient.getInstance().setScreen(new NodeCopierConfigScreen(stack, offHand));
				});
		addChild(new ClickableWidget(connectModeButton));

		final ButtonWidgetExtension copyAllModeButton = new ButtonWidgetExtension(
				panelX + 20, panelY + 54, PANEL_W - 40, 20,
				TextHelper.literal((mode == ItemNodeCopier.MODE_COPY_ALL ? "\u2713 " : "\u25CB ")
						+ TextHelper.translatable("gui.s1mtr.node_copier.mode_copy_all").getString()),
				btn -> {
					ItemNodeCopier.setMode(stack, ItemNodeCopier.MODE_COPY_ALL);
					sendModeToServer(ItemNodeCopier.MODE_COPY_ALL);
					MinecraftClient.getInstance().setScreen(new NodeCopierConfigScreen(stack, offHand));
				});
		addChild(new ClickableWidget(copyAllModeButton));

		final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(
				panelX + 20, panelY + PANEL_H - 30, PANEL_W - 40, 20,
				TextHelper.translatable("gui.done"), btn -> MinecraftClient.getInstance().setScreen(null));
		addChild(new ClickableWidget(doneButton));
	}

	/** 把模式同步到服务端手持物品 NBT,防止服务端重新同步背包时覆盖客户端修改。 */
	private void sendModeToServer(int mode) {
		top.s1metro.s1mtr.client.S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
				new top.s1metro.s1mtr.network.PacketS1mtrSaveNodeCopierMode(mode, offHand));
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
