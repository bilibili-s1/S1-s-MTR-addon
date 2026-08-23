package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.S1mtraddonClient;
import top.s1metro.s1mtr.network.PacketS1mtrTeleportToStation;

/**
 * 车站传送确认菜单。
 * <p>
 * 由 {@link top.s1metro.s1mtr.item.ItemStationTeleporter} 在玩家右键命中站点区域时打开。
 * 居中显示一个面板:
 * <ul>
 *     <li>标题: 站名</li>
 *     <li>按钮 1: 传送到此车站 (点击后关闭界面,发送传送网络包)</li>
 *     <li>按钮 2: 取消 (仅关闭界面)</li>
 * </ul>
 */
public class StationTeleportScreen extends MTRScreenBase {

	private static final int PANEL_W = 280;
	private static final int BTN_H = 20;

	private final Station station;
	private final Position teleportCenter;
	private ButtonWidgetExtension teleportButton;
	private ButtonWidgetExtension cancelButton;

	public StationTeleportScreen(Station station, Position teleportCenter) {
		super();
		this.station = station;
		this.teleportCenter = teleportCenter;
	}

	@Override
	protected void init2() {
		super.init2();

		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - 140) / 2;

		final String stationName = station == null || station.getName() == null || station.getName().isEmpty()
				? "Untitled Station"
				: station.getName();

		final MutableText teleportLabel = TextHelper.translatable(
				"gui.s1mtr.teleport_station_confirm", stationName);
		final MutableText cancelLabel = TextHelper.translatable("gui.cancel");

		final int btnX = panelX + 20;
		final int btnW = PANEL_W - 40;
		final int btn1Y = panelY + 64;
		final int btn2Y = panelY + 92;

		teleportButton = new ButtonWidgetExtension(
				btnX, btn1Y, btnW, BTN_H,
				teleportLabel,
				button -> confirmAndTeleport());
		cancelButton = new ButtonWidgetExtension(
				btnX, btn2Y, btnW, BTN_H,
				cancelLabel,
				button -> MinecraftClient.getInstance().setScreen(null));

		addChild(new ClickableWidget(teleportButton));
		addChild(new ClickableWidget(cancelButton));
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - 140) / 2;
		final int panelX2 = panelX + PANEL_W;
		final int panelY2 = panelY + 140;

		// 半透明面板 (MTR 用 GuiDrawing.drawRectangle 绘制矩形)
		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		// 背景
		guiDrawing.drawRectangle(panelX, panelY, panelX2, panelY2, 0xFE202028);
		// 边框 (上下左右四条线)
		guiDrawing.drawRectangle(panelX, panelY, panelX2, panelY + 1, 0xFFFFFFFF);
		guiDrawing.drawRectangle(panelX, panelY2 - 1, panelX2, panelY2, 0xFF404050);
		guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY2, 0xFF808090);
		guiDrawing.drawRectangle(panelX2 - 1, panelY, panelX2, panelY2, 0xFF808090);
		guiDrawing.finishDrawingRectangle();

		// 标题: 站名
		final String stationName = station == null || station.getName() == null || station.getName().isEmpty()
				? "Untitled Station"
				: station.getName();
		final MutableText title = TextHelper.translatable("gui.s1mtr.teleport_station_title", stationName);
		graphicsHolder.drawCenteredText(
				title,
				width / 2, panelY + 20,
				0xFFFFFF);

		// 坐标提示 (X, Y, Z)
		final MutableText coords = TextHelper.translatable(
				"gui.s1mtr.teleport_station_coords",
				teleportCenter.getX(), teleportCenter.getY(), teleportCenter.getZ());
		graphicsHolder.drawCenteredText(
				coords,
				width / 2, panelY + 42,
				0xCCCCCC);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	private void confirmAndTeleport() {
		MinecraftClient.getInstance().setScreen(null);
		// 若车站中心未获取到有效高度 (y 过小通常表示该车站没有确定的地面高度),
		// 则发送 NaN, 服务端会保持玩家当前高度 (相当于命令中的 ~ 相对坐标)
		final long y = teleportCenter.getY();
		final double finalY = y <= 1 ? Double.NaN : y;
		S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(new PacketS1mtrTeleportToStation(
				teleportCenter.getX(),
				finalY,
				teleportCenter.getZ()));
	}
}
