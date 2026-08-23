package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Position;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.S1mtraddonClient;
import top.s1metro.s1mtr.network.PacketS1mtrConnectRails;

/**
 * 连接轨道前弹出的限速输入界面。
 * <p>
 * 玩家选择两个节点后，本界面提示输入限速(km/h)，确认后发送连接请求到服务端，
 * 在两点之间创建一条双向轨道。
 */
public class RailConnectSpeedScreen extends MTRScreenBase {

	private static final int PANEL_W = 300;
	private static final int PANEL_H = 180;
	private static final int BTN_H = 20;

	private final Position position1;
	private final Position position2;
	private TextFieldWidgetExtension speedField;

	public RailConnectSpeedScreen(Position position1, Position position2) {
		super();
		this.position1 = position1;
		this.position2 = position2;
	}

	@Override
	protected void init2() {
		super.init2();

		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;
		final int btnX = panelX + 20;
		final int btnW = PANEL_W - 40;

		speedField = new TextFieldWidgetExtension(
				btnX + 80, panelY + 60, btnW - 80, 20, 5,
				TextCase.DEFAULT, "[^\\dSsPpTt]", "80");
		addChild(new ClickableWidget(speedField));

		final MutableText confirmLabel = TextHelper.translatable("gui.s1mtr.rail_connector.confirm");
		final MutableText cancelLabel = TextHelper.translatable("gui.cancel");
		final MutableText backLabel = TextHelper.translatable("gui.s1mtr.rail_connector.back_to_map");

		final ButtonWidgetExtension confirmButton = new ButtonWidgetExtension(
				btnX, panelY + 92, btnW, BTN_H, confirmLabel, button -> confirmAndConnect());
		final ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(
				btnX, panelY + 118, btnW, BTN_H, cancelLabel,
				button -> MinecraftClient.getInstance().setScreen(null));
		final ButtonWidgetExtension backButton = new ButtonWidgetExtension(
				btnX + btnW - 90, panelY + 146, 90, 15, backLabel,
				button -> MinecraftClient.getInstance().setScreen(new RailNetworkMapScreen()));

		addChild(new ClickableWidget(confirmButton));
		addChild(new ClickableWidget(cancelButton));
		addChild(new ClickableWidget(backButton));
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;
		final int panelX2 = panelX + PANEL_W;
		final int panelY2 = panelY + PANEL_H;

		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(panelX, panelY, panelX2, panelY2, 0xFE202028);
		guiDrawing.drawRectangle(panelX, panelY, panelX2, panelY + 1, 0xFFFFFFFF);
		guiDrawing.drawRectangle(panelX, panelY2 - 1, panelX2, panelY2, 0xFF404050);
		guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY2, 0xFF808090);
		guiDrawing.drawRectangle(panelX2 - 1, panelY, panelX2, panelY2, 0xFF808090);
		guiDrawing.finishDrawingRectangle();

		final MutableText title = TextHelper.translatable("gui.s1mtr.rail_connector.connect_title");
		graphicsHolder.drawCenteredText(title, width / 2, panelY + 16, 0xFFFFFF);

		final MutableText coords1 = TextHelper.translatable("gui.s1mtr.rail_connector.node1",
				position1.getX(), position1.getY(), position1.getZ());
		graphicsHolder.drawCenteredText(coords1, width / 2, panelY + 34, 0xCCCCCC);
		final MutableText coords2 = TextHelper.translatable("gui.s1mtr.rail_connector.node2",
				position2.getX(), position2.getY(), position2.getZ());
		graphicsHolder.drawCenteredText(coords2, width / 2, panelY + 46, 0xCCCCCC);

		final MutableText speedLabel = TextHelper.translatable("gui.s1mtr.rail_connector.speed");
		graphicsHolder.drawText(speedLabel, (width - PANEL_W) / 2 + 20, panelY + 64,
				-1, false, GraphicsHolder.getDefaultLight());

		final MutableText speedHint = TextHelper.translatable("gui.s1mtr.rail_connector.speed_hint");
		graphicsHolder.drawCenteredText(speedHint, width / 2, panelY + 84, 0xFFAA88);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	private void confirmAndConnect() {
		final String text = speedField.getText2().trim();
		int railType = PacketS1mtrConnectRails.TYPE_NORMAL;
		long speed = 80;
		switch (text.toLowerCase()) {
			case "s":
				railType = PacketS1mtrConnectRails.TYPE_SIDING;
				break;
			case "p":
				railType = PacketS1mtrConnectRails.TYPE_PLATFORM;
				break;
			case "t":
				railType = PacketS1mtrConnectRails.TYPE_TURNBACK;
				break;
			default:
				try {
					speed = Math.max(1, Long.parseLong(text));
				} catch (NumberFormatException ignored) {
				}
				break;
		}
		MinecraftClient.getInstance().setScreen(null);
		S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
				new PacketS1mtrConnectRails(position1, position2, speed, railType));
	}
}
