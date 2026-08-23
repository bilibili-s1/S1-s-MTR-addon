package top.s1metro.s1mtr.client.builder;

import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.block.BlockNode;

/**
 * 复合构建器放样核心(反向映射算法)。
 * <p>
 * 旧实现是"正向采样":沿轨道每隔 1 格取一个中心点,把整个剖面铺上去。
 * 曲线轨道上,外侧相邻两个截面之间的方块会被漏掉,产生空洞。
 * <p>
 * 新实现参考 WorldEdit 道路放样脚本(loft.js)的 roadmap 思路,改为"反向映射":
 * <ol>
 *   <li>沿轨道密集采样中心线与横向方向(每 0.25 格一个采样点);</li>
 *   <li>计算轨道包围盒并向外扩 7 格作为候选区域;</li>
 *   <li>对候选区域内每个整数方块位置,找到离它最近的轨道采样点(仅看水平距离),
 *       用该点的横向方向把方块投影到剖面坐标 (gx, gy):gx 为带符号的水平偏移,
 *       gy 为相对轨道中心线的竖直偏移;同时用该点的切线方向计算方块的纵向坐标,
 *       超出轨道两端(含半格容差)的方块不放置,避免放样比轨道长;</li>
 *   <li>查询当前纵向距离对应的剖面(支持分层调度),命中非空格子则放置方块。</li>
 * </ol>
 * 因为每个世界方块位置只被处理一次、只依赖"它离轨道有多远/在哪一侧",
 * 曲线内外侧的方块都能被完整覆盖,不会出现正向采样在曲线外侧漏方块产生的洞。
 * 放样时跳过轨道节点方块({@link BlockNode}),保护轨道本体。
 * <p>
 * 支持分层调度:根据轨道纵向距离选择不同的剖面,实现"5 格无灯→1 格有灯→5 格无灯..."的循环放样。
 */
public final class CompositeBuilder {

	private static final double SAMPLE_STEP = 0.25;
	private static final double DIRECTION_DELTA = 0.1;
	private static final int RADIUS = CompositeProfile.CENTER;

	private CompositeBuilder() {
	}

	/** 沿轨道执行单剖面放样(向后兼容入口)。 */
	public static void build(ServerWorld serverWorld, Rail rail, CompositeProfile profile) {
		if (profile == null) {
			return;
		}
		final CompositeLayerSchedule schedule = new CompositeLayerSchedule();
		schedule.entries().get(0).profile = profile;
		schedule.entries().get(0).length = CompositeLayerSchedule.LENGTH_INFINITE;
		build(serverWorld, rail, schedule);
	}

	/**
	 * 沿轨道执行分层放样。
	 *
	 * @param serverWorld 服务端世界
	 * @param rail        目标轨道
	 * @param schedule    分层调度表(至少一个剖面)
	 */
	public static void build(ServerWorld serverWorld, Rail rail, CompositeLayerSchedule schedule) {
		final World world = serverWorld.data;
		if (world == null || rail == null || schedule == null || schedule.size() == 0) {
			return;
		}
		final double length = rail.railMath.getLength();
		if (length <= SAMPLE_STEP) {
			return;
		}

		// 1. 密集采样中心线,同时计算每个采样点处的横向(左右)方向
		final int sampleCount = (int) Math.ceil(length / SAMPLE_STEP) + 1;
		final Vector[] centers = new Vector[sampleCount];
		final Vector[] normals = new Vector[sampleCount];
		final Vector[] tangents = new Vector[sampleCount];
		final double[] distances = new double[sampleCount];
		for (int i = 0; i < sampleCount; i++) {
			distances[i] = Math.min(i * SAMPLE_STEP, length);
			centers[i] = rail.railMath.getPosition(distances[i], false);
			tangents[i] = getTangent(rail, distances[i], length);
			normals[i] = tangents[i].rotateY(Math.PI / 2);
		}

		// 2. 轨道包围盒向外扩剖面半径,得到候选区域
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (final Vector center : centers) {
			minX = Math.min(minX, (int) Math.floor(center.x()));
			maxX = Math.max(maxX, (int) Math.floor(center.x()));
			minY = Math.min(minY, (int) Math.floor(center.y()));
			maxY = Math.max(maxY, (int) Math.floor(center.y()));
			minZ = Math.min(minZ, (int) Math.floor(center.z()));
			maxZ = Math.max(maxZ, (int) Math.floor(center.z()));
		}
		minX -= RADIUS;
		maxX += RADIUS;
		minY -= RADIUS;
		maxY += RADIUS;
		minZ -= RADIUS;
		maxZ += RADIUS;

		// 3. 反向映射:每个 (x, z) 列找最近的轨道采样点(只看水平距离),再对列内每个 y 放置
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				final int best = findClosest(centers, x, z);
				if (best < 0) {
					continue;
				}
				final Vector center = centers[best];
				final Vector normal = normals[best];

				final Vector tangent = tangents[best];
				final double along = (x - center.x()) * tangent.x() + (z - center.z()) * tangent.z();
				final double sBlock = distances[best] + along;
				if (sBlock < -0.5 || sBlock > length + 0.5) {
					continue;
				}

				// 横向坐标以轨道中心线所在方块为基准(而非精确中心线),避免东西/南北走向
				// 因轨道中心落在方块边界而产生整格偏移(此前东西走向放样会整体偏南一格)。
				final double offsetX = x - Math.floor(center.x());
				final double offsetZ = z - Math.floor(center.z());
				final double gxF = offsetX * normal.x() + offsetZ * normal.z();
				if (gxF < -(RADIUS + 0.5) || gxF > RADIUS + 0.5) {
					continue;
				}
				final int gx = (int) Math.round(gxF);
				if (gx < -RADIUS || gx > RADIUS - 1) {
					continue;
				}

				// 根据 sBlock 选取当前剖面
				final int layerIndex = schedule.getLayerIndexAt(sBlock);
				if (layerIndex < 0 || layerIndex >= schedule.size()) {
					continue;
				}
				final CompositeProfile profile = schedule.get(layerIndex).profile;

				for (int y = minY; y <= maxY; y++) {
					final double gyF = y - center.y();
					final int gy = (int) Math.round(gyF);
					if (gy < -RADIUS || gy > RADIUS - 1) {
						continue;
					}
					final String cellString = profile.getCell(gx, gy);
					if (cellString == null) {
						continue;
					}
					final BlockPos blockPos = new BlockPos(x, y, z);
					if (!canPlace(world, blockPos)) {
						continue;
					}
					final BlockState state = resolveBlockState(cellString, tangent);
					if (state != null) {
						world.setBlockState(blockPos, state, 3);
					}
				}
			}
		}
	}

	private static int findClosest(Vector[] centers, int x, int z) {
		int best = -1;
		double bestDistSquared = Double.MAX_VALUE;
		for (int i = 0; i < centers.length; i++) {
			final double dx = x - centers[i].x();
			final double dz = z - centers[i].z();
			final double distSquared = dx * dx + dz * dz;
			if (distSquared < bestDistSquared) {
				bestDistSquared = distSquared;
				best = i;
			}
		}
		return best;
	}

	private static Vector getTangent(Rail rail, double distance, double length) {
		final Vector p1 = rail.railMath.getPosition(Math.max(distance - DIRECTION_DELTA, 0), false);
		final Vector p2 = rail.railMath.getPosition(Math.min(distance + DIRECTION_DELTA, length), false);
		return new Vector(p2.x() - p1.x(), 0, p2.z() - p1.z()).normalize();
	}

	private static boolean canPlace(World world, BlockPos pos) {
		return world.getBlockEntity(pos) == null && !(world.getBlockState(pos).getBlock() instanceof BlockNode);
	}

	/**
	 * 解析 cell 字符串为 BlockState,并根据轨道切线方向应用旋转。
	 * <p>
	 * 旋转语义:cell 中存储的 BlockState 默认朝北,放样时按切线方向(N/E/S/W → 0/1/2/3)旋转。
	 * 空气方块不应用旋转。
	 *
	 * @param cellString BlockState 字符串(如 {@code minecraft:chest[facing=north]})
	 * @param tangent    轨道切线方向单位向量
	 */
	private static BlockState resolveBlockState(String cellString, Vector tangent) {
		if (cellString == null || cellString.isEmpty()) {
			return null;
		}
		final BlockState state = CompositeProfile.parseBlockState(cellString);
		if (state == null) {
			return null;
		}
		if (state.getBlock() == Blocks.AIR) {
			return state;
		}
		final int railRotation = tangentToRotationIndex(tangent);
		if (railRotation != 0) {
			return state.rotate(BlockRotation.values()[railRotation]);
		}
		return state;
	}

	/**
	 * 将轨道切线方向转换为 0-3 的旋转索引(NONE/CW90/CW180/CCW90)。
	 * <p>
	 * MC 坐标系:-Z=北, +X=东, +Z=南, -X=西。
	 */
	private static int tangentToRotationIndex(Vector tangent) {
		final double x = tangent.x();
		final double z = tangent.z();
		if (Math.abs(x) > Math.abs(z)) {
			return x > 0 ? 1 : 3;
		} else {
			return z > 0 ? 2 : 0;
		}
	}
}
