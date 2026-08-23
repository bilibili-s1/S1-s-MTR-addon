package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketRequestData;
import org.mtr.mod.screen.MTRScreenBase;
import org.mtr.core.operation.DataRequest;
import top.s1metro.s1mtr.client.S1mtraddonClient;
import top.s1metro.s1mtr.network.PacketS1mtrTeleportToStation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 车站列表界面。
 * <p>
 * 从 {@link MinecraftClientData#getInstance()} 读取所有车站，以滚动列表形式展示。
 * 每行一个车站名，点击后弹出确认菜单 {@link StationTeleportScreen}。
 */
public class StationListScreen extends MTRScreenBase {

	private static final int ENTRY_H = 18;
	private static final int ENTRY_GAP = 2;
	private static final int LIST_W = 300;
	private static final int VISIBLE_ENTRIES = 12;

	private final List<Station> stations = new ArrayList<>();
	private int scrollOffset = 0;
	private ButtonWidgetExtension upButton;
	private ButtonWidgetExtension downButton;

	@Override
	protected void init2() {
		super.init2();

		loadStations();
		requestAllStations();
	}

	/** 从客户端主数据读取车站并按名称排序。 */
	private void loadStations() {
		stations.clear();
		final ObjectArraySet<Station> stationSet = MinecraftClientData.getInstance().stations;
		if (stationSet != null) {
			stations.addAll(stationSet);
		}
		// 按名称排序
		stations.sort((a, b) -> {
			final String na = a.getName() == null ? "" : a.getName();
			final String nb = b.getName() == null ? "" : b.getName();
			return na.compareToIgnoreCase(nb);
		});

		scrollOffset = 0;
		rebuildButtons();
	}

	/**
	 * 请求服务端同步完整数据(包含所有车站,包括未加载区块的)。
	 * MTR 的 PacketRequestData 会让服务端返回完整数据并通过 DataResponse 写入 getInstance()。
	 */
	private void requestAllStations() {
		try {
			final org.mtr.mapping.holder.MinecraftClient mc = org.mtr.mapping.holder.MinecraftClient.getInstance();
			final org.mtr.mapping.holder.BlockPos blockPos = mc.getPlayerMapped().getBlockPos();
			final Position playerPos = new Position(blockPos.getX(), blockPos.getY(), blockPos.getZ());
			// 请求半径设为极大值,以覆盖整个存档的所有车站
			final DataRequest request = new DataRequest(UUID.randomUUID(), playerPos, Long.MAX_VALUE);
			S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(new PacketRequestData(request));
		} catch (Exception ignored) {
		}
	}

	/**
	 * 响应是异步到达的,渲染时检测车站数量是否增加(说明服务端完整数据已同步),若增加则刷新列表。
	 */
	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		final ObjectArraySet<Station> current = MinecraftClientData.getInstance().stations;
		if (current != null && current.size() > stations.size()) {
			loadStations();
		}
		superRender(graphicsHolder, mouseX, mouseY, delta);
	}

	private void rebuildButtons() {
		// 清除之前的按钮 (保留子组件列表)
		clearChildren();

		final int listX = (width - LIST_W) / 2;
		final int listY = 40;
		final int btnX = listX + 8;
		final int btnW = LIST_W - 16;

		final int maxOffset = Math.max(0, stations.size() - VISIBLE_ENTRIES);
		if (scrollOffset > maxOffset) scrollOffset = maxOffset;
		if (scrollOffset < 0) scrollOffset = 0;

		final int shown = Math.min(VISIBLE_ENTRIES, stations.size() - scrollOffset);
		for (int i = 0; i < shown; i++) {
			final int idx = scrollOffset + i;
			final Station station = stations.get(idx);
			final String name = station.getName() == null || station.getName().isEmpty()
					? "Untitled Station" : station.getName();
			final int btnY = listY + i * (ENTRY_H + ENTRY_GAP);

			final MutableText label = TextHelper.literal(name);
			final ButtonWidgetExtension btn = new ButtonWidgetExtension(
					btnX, btnY, btnW, ENTRY_H,
					label,
					button -> selectStation(station));
			addChild(new ClickableWidget(btn));
		}

		// 上下翻页按钮
		final int navBtnX = listX + LIST_W + 4;
		final int navBtnY = listY;
		upButton = new ButtonWidgetExtension(
				navBtnX, navBtnY, 20, ENTRY_H * 2 + ENTRY_GAP,
				TextHelper.literal("▲"),
				button -> {
					scrollOffset = Math.max(0, scrollOffset - VISIBLE_ENTRIES);
					rebuildButtons();
				});
		downButton = new ButtonWidgetExtension(
				navBtnX, navBtnY + (ENTRY_H + ENTRY_GAP) * 2, 20, ENTRY_H * 2 + ENTRY_GAP,
				TextHelper.literal("▼"),
				button -> {
					scrollOffset = Math.min(maxOffset, scrollOffset + VISIBLE_ENTRIES);
					rebuildButtons();
				});
		addChild(new ClickableWidget(upButton));
		addChild(new ClickableWidget(downButton));
	}

	private void superRender(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		final int listX = (width - LIST_W) / 2;
		final int listY = 40;
		final int listH = VISIBLE_ENTRIES * (ENTRY_H + ENTRY_GAP);
		final int listX2 = listX + LIST_W;
		final int listY2 = listY + listH;

		// 面板背景
		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(listX - 4, listY - 4, listX2 + 28, listY2 + 4, 0xFE202028);
		guiDrawing.drawRectangle(listX - 4, listY - 4, listX2 + 28, listY - 3, 0xFF808090);
		guiDrawing.drawRectangle(listX - 4, listY2 + 3, listX2 + 28, listY2 + 4, 0xFF808090);
		guiDrawing.drawRectangle(listX - 4, listY - 4, listX - 3, listY2 + 4, 0xFF808090);
		guiDrawing.drawRectangle(listX2 + 27, listY - 4, listX2 + 28, listY2 + 4, 0xFF808090);
		guiDrawing.finishDrawingRectangle();

		// 标题
		final MutableText title = TextHelper.translatable("gui.s1mtr.teleport_station_list_title");
		graphicsHolder.drawCenteredText(title, width / 2, 16, 0xFFFFFF);

		// 底部提示
		final MutableText hint = TextHelper.translatable(
				"gui.s1mtr.teleport_station_count", stations.size());
		graphicsHolder.drawCenteredText(hint, width / 2, listY + listH + 10, 0xAAAAAA);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		if (amount > 0) {
			scrollOffset = Math.max(0, scrollOffset - 1);
		} else if (amount < 0) {
			final int maxOffset = Math.max(0, stations.size() - VISIBLE_ENTRIES);
			scrollOffset = Math.min(maxOffset, scrollOffset + 1);
		}
		rebuildButtons();
		return true;
	}

	private void selectStation(Station station) {
		final Position center = station.getCenter();
		MinecraftClient.getInstance().setScreen(new StationTeleportScreen(station, center));
	}
}
