package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.packet.ClientPacketHelper;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.network.PacketS1mtrTeleportToStation;

import java.lang.reflect.Method;

/**
 * 车站右键菜单。
 * <p>
 * 由 {@link top.s1metro.s1mtr.mixin.client.WidgetMapMixin} 在地图上右键车站时弹出。
 * 提供"传送"和"编辑"两个操作按钮。
 */
public class StationRightClickMenuScreen extends MTRScreenBase {

	private static final int PANEL_W = 260;
	private static final int BTN_H = 20;

	private final Station station;
	private final Position teleportCenter;

	public StationRightClickMenuScreen(Station station, Position teleportCenter) {
		super();
		this.station = station;
		this.teleportCenter = teleportCenter;
	}

	@Override
	protected void init2() {
		super.init2();

		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - 130) / 2;

		final String stationName = getStationName();

		final MutableText teleportLabel = TextHelper.translatable(
				"gui.s1mtr.teleport_station_confirm", stationName);
		final MutableText editLabel = TextHelper.translatable(
				"gui.s1mtr.edit_station", stationName);
		final MutableText cancelLabel = TextHelper.translatable("gui.cancel");

		final int btnX = panelX + 20;
		final int btnW = PANEL_W - 40;
		final int btn1Y = panelY + 54;
		final int btn2Y = panelY + 80;
		final int btn3Y = panelY + 106;

		addChild(new ClickableWidget(new ButtonWidgetExtension(
				btnX, btn1Y, btnW, BTN_H, teleportLabel,
				button -> doTeleport())));
		addChild(new ClickableWidget(new ButtonWidgetExtension(
				btnX, btn2Y, btnW, BTN_H, editLabel,
				button -> doEdit())));
		addChild(new ClickableWidget(new ButtonWidgetExtension(
				btnX, btn3Y, btnW, BTN_H, cancelLabel,
				button -> MinecraftClient.getInstance().setScreen(null))));
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - 130) / 2;
		final int panelX2 = panelX + PANEL_W;
		final int panelY2 = panelY + 130;

		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(panelX, panelY, panelX2, panelY2, 0xFE202028);
		guiDrawing.drawRectangle(panelX, panelY, panelX2, panelY + 1, 0xFFFFFFFF);
		guiDrawing.drawRectangle(panelX, panelY2 - 1, panelX2, panelY2, 0xFF404050);
		guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY2, 0xFF808090);
		guiDrawing.drawRectangle(panelX2 - 1, panelY, panelX2, panelY2, 0xFF808090);
		guiDrawing.finishDrawingRectangle();

		final String stationName = getStationName();
		final MutableText title = TextHelper.translatable("gui.s1mtr.teleport_station_title", stationName);
		graphicsHolder.drawCenteredText(title, width / 2, panelY + 18, 0xFFFFFF);

		final MutableText coords = TextHelper.translatable(
				"gui.s1mtr.teleport_station_coords",
				teleportCenter.getX(), teleportCenter.getY(), teleportCenter.getZ());
		graphicsHolder.drawCenteredText(coords, width / 2, panelY + 36, 0xCCCCCC);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	private void doTeleport() {
		MinecraftClient.getInstance().setScreen(null);
		top.s1metro.s1mtr.client.S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
				new PacketS1mtrTeleportToStation(
						teleportCenter.getX(),
						teleportCenter.getY(),
						teleportCenter.getZ()));
	}

	private void doEdit() {
		MinecraftClient.getInstance().setScreen(null);
		// 用反射调用 ClientPacketHelper.openDashboardScreen(TransportMode, ScreenType, long)
		// 因为 ScreenType 是 MTR mapping 层的枚举,编译时签名与 javap 查看的不一致
		try {
			for (Method m : ClientPacketHelper.class.getMethods()) {
				if (!m.getName().equals("openDashboardScreen")) {
					continue;
				}
				final Class<?>[] paramTypes = m.getParameterTypes();
				if (paramTypes.length != 3) {
					continue;
				}
				final Class<?> screenTypeClass = paramTypes[1];
				final Object[] enumConstants = screenTypeClass.getEnumConstants();
				Object stationType = null;
				if (enumConstants != null) {
					for (Object e : enumConstants) {
						if (e.toString().equalsIgnoreCase("STATION")) {
							stationType = e;
							break;
						}
					}
					if (stationType == null) {
						stationType = enumConstants[0];
					}
				}
				m.invoke(null, TransportMode.TRAIN, stationType, station.getId());
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String getStationName() {
		return station == null || station.getName() == null || station.getName().isEmpty()
				? "Untitled Station" : station.getName();
	}
}
