package top.s1metro.s1mtr.client.screen;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.screen.WidgetMap;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 基于 MTR {@link WidgetMap}（铁路仪表板地图）扩展的轨道网络地图组件。
 * <p>
 * 复用 MTR 的完整底图渲染、车站/车辆段区域标注、缩放平移等能力。
 * 节点集合来自存档内所有轨道的端点（即轨道节点），玩家必须点击已有的轨道节点来连接，
 * 依次选中两个节点后通过 {@link OnConnectRequested} 回调通知宿主屏幕。
 */
public class RailNetworkMapWidget extends WidgetMap {

	private static final Method S1MTR_DRAW_FROM_WORLD_COORDS;
	private static final Method S1MTR_DRAW_RECT_FROM_WORLD_COORDS;
	private static final Method S1MTR_AREA_VALID_CORNERS;

	static {
		Method drawFrom = null;
		Method drawRect = null;
		Method validCorners = null;
		try {
			drawFrom = WidgetMap.class.getDeclaredMethod("drawFromWorldCoords",
					double.class, double.class, java.util.function.BiConsumer.class);
			drawFrom.setAccessible(true);
			drawRect = WidgetMap.class.getDeclaredMethod("drawRectangleFromWorldCoords",
					org.mtr.mapping.mapper.GuiDrawing.class, double.class, double.class, double.class, double.class, int.class);
			drawRect.setAccessible(true);
			validCorners = org.mtr.core.data.AreaBase.class.getDeclaredMethod("validCorners", org.mtr.core.data.AreaBase.class);
			validCorners.setAccessible(true);
		} catch (Exception ignored) {
		}
		S1MTR_DRAW_FROM_WORLD_COORDS = drawFrom;
		S1MTR_DRAW_RECT_FROM_WORLD_COORDS = drawRect;
		S1MTR_AREA_VALID_CORNERS = validCorners;
	}

	private final List<Rail> rails = new ArrayList<>();
	private final List<Position> nodes = new ArrayList<>();

	private Position selectedNode;
	private final OnConnectRequested onConnectRequested;

	public RailNetworkMapWidget(OnConnectRequested onConnectRequested) {
		super(TransportMode.TRAIN, (p1, p2) -> {
		}, () -> {
		}, id -> {
		}, savedRailBase -> {
		}, (x, y) -> false);
		this.onConnectRequested = onConnectRequested;
		loadData();
	}

	private void loadData() {
		rails.clear();
		nodes.clear();
		final ObjectArraySet<Rail> allRails = MinecraftClientData.getInstance().rails;
		if (allRails == null) {
			return;
		}
		final Set<Position> nodeSet = new LinkedHashSet<>();
		for (Rail rail : allRails) {
			final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
			final Position p1 = accessor.s1mtr$getPosition1();
			final Position p2 = accessor.s1mtr$getPosition2();
			rails.add(rail);
			nodeSet.add(p1);
			nodeSet.add(p2);
		}
		// 补充扫描玩家附近的孤立轨道节点 (未连接任何轨道的 BlockNode 方块)
		scanIsolatedNodes(nodeSet);
		nodes.addAll(nodeSet);
	}

	/**
	 * 扫描玩家周围世界中的 BlockNode 方块，把孤立（未连接轨道）的节点也加入集合，
	 * 使它们同样可以被点击连接。扫描范围限定在玩家附近且只扫描已加载区块，
	 * 避免遍历整个世界造成卡顿。
	 */
	private static void scanIsolatedNodes(Set<Position> nodeSet) {
		final ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
		if (world == null) {
			return;
		}
		final BlockPos playerPos = MinecraftClient.getInstance().getPlayerMapped().getBlockPos();
		final int px = playerPos.getX();
		final int pz = playerPos.getZ();
		final int py = playerPos.getY();
		final int range = 96;
		final int minY = Math.max(-64, py - 64);
		final int maxY = Math.min(320, py + 64);
		for (int x = px - range; x <= px + range; x++) {
			for (int z = pz - range; z <= pz + range; z++) {
				if (!world.data.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					if (world.getBlockState(new BlockPos(x, y, z)).data.getBlock() instanceof BlockNode) {
						nodeSet.add(new Position(x, y, z));
					}
				}
			}
		}
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		// MTR 地图自身的渲染（底图、平台等区域）
		super.render(graphicsHolder, mouseX, mouseY, delta);

		// WidgetMap 内部使用 dashboardInstance(仅打开 MTR 仪表板时才同步) 绘制车站区域,
		// 我们这里用主数据实例 getInstance()(始终与客户端同步) 补充绘制车站范围, 保证始终可见。
		drawStationAreas(graphicsHolder);
	}

	private void drawStationAreas(GraphicsHolder graphicsHolder) {
		if (S1MTR_DRAW_RECT_FROM_WORLD_COORDS == null || S1MTR_AREA_VALID_CORNERS == null) {
			return;
		}
		final org.mtr.mapping.mapper.GuiDrawing guiDrawing = new org.mtr.mapping.mapper.GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		try {
			final org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet<org.mtr.core.data.Station> stations =
					MinecraftClientData.getInstance().stations;
			if (stations == null) {
				return;
			}
			for (org.mtr.core.data.Station station : stations) {
				if ((boolean) S1MTR_AREA_VALID_CORNERS.invoke(null, station)) {
					// 颜色与 MTR 一致: 0x7F000000 + station.getColor()
					final int color = 0x7F000000 + station.getColor();
					S1MTR_DRAW_RECT_FROM_WORLD_COORDS.invoke(this, guiDrawing,
							(double) station.getMinX(), (double) station.getMinZ(),
							(double) station.getMaxX(), (double) station.getMaxZ(), color);
				}
			}
		} catch (Exception ignored) {
		} finally {
			guiDrawing.finishDrawingRectangle();
		}
	}

	@Override
	public boolean mouseClicked2(double mouseX, double mouseY, int button) {
		if (button == 0) {
			final Position clicked = findNodeNear(mouseX, mouseY);
			if (clicked != null) {
				if (selectedNode == null) {
					selectedNode = clicked;
				} else if (!selectedNode.equals(clicked)) {
					final Position first = selectedNode;
					selectedNode = null;
					if (onConnectRequested != null) {
						onConnectRequested.onConnectRequested(first, clicked);
					}
				} else {
					selectedNode = null;
				}
				return true;
			}
		}
		return super.mouseClicked2(mouseX, mouseY, button);
	}

	public Position getSelectedNode() {
		return selectedNode;
	}

	public int getRailCount() {
		return rails.size();
	}

	public int getNodeCount() {
		return nodes.size();
	}

	private Position findNodeNear(double mouseX, double mouseY) {
		if (S1MTR_DRAW_FROM_WORLD_COORDS == null) {
			return null;
		}
		// mouseClicked2 传入的是绝对屏幕坐标，需转为相对本 widget 的坐标
		final double localX = mouseX - getX2();
		final double localY = mouseY - getY2();
		final double threshold = 6;
		Position nearest = null;
		double nearestDist = threshold * threshold;
		for (Position node : nodes) {
			final double[] screen = worldToScreen(node.getX(), node.getZ());
			if (screen == null) {
				continue;
			}
			final double dx = screen[0] - localX;
			final double dz = screen[1] - localY;
			final double dist = dx * dx + dz * dz;
			if (dist <= nearestDist) {
				nearestDist = dist;
				nearest = node;
			}
		}
		return nearest;
	}

	private double[] worldToScreen(long wx, long wz) {
		if (S1MTR_DRAW_FROM_WORLD_COORDS == null) {
			return null;
		}
		final double[] result = new double[2];
		final boolean[] visible = new boolean[1];
		try {
			S1MTR_DRAW_FROM_WORLD_COORDS.invoke(this, (double) wx, (double) wz,
					(BiConsumer<Double, Double>) (sx, sz) -> {
						result[0] = sx;
						result[1] = sz;
						visible[0] = true;
					});
		} catch (Exception e) {
			return null;
		}
		return visible[0] ? result : null;
	}

	/** 点击两个节点后，宿主屏幕据此切换到速度输入界面。 */
	@FunctionalInterface
	public interface OnConnectRequested {
		void onConnectRequested(Position position1, Position position2);
	}
}
