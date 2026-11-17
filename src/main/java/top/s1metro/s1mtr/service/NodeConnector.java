package top.s1metro.s1mtr.service;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.data.RailType;
import org.mtr.mod.packet.PacketUpdateData;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlockEntity;
import top.s1metro.s1mtr.mixin.AngleExtra;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * 万向节点的连接几何与建轨工具（最小可用版）。
 * <p>
 * 保留方速实现里的三块核心：两点直线角、最大半径圆弧切向角、按连接器类型用任意精度 Angle 建轨。
 * 砍掉了节点平移、俯仰/翻滚、外轨超高、逐轨道倾斜与自定义 RailMath 几何内核 —— 那些依赖方速自研的
 * sowcer/OBJ 渲染栈与 RailPoseExtra，本模组不需要，也不会改动原版轨道几何。
 *
 * <p>移植自 FangSu For MTR 4（MIT），按 Yarn 映射重写。
 */
public final class NodeConnector {

	private NodeConnector() {
	}

	/** 归一化角度到 [0,360)。 */
	public static double normalizeDegrees(double deg) {
		deg = deg % 360.0;
		if (deg < 0) {
			deg += 360.0;
		}
		return deg;
	}

	/** 水平面上两端点的连线角度（度，0=E, 90=S, 180=W, 270=N）。 */
	public static double straightAngle(BlockPos a, BlockPos b) {
		final double dx = b.getX() - a.getX();
		final double dz = b.getZ() - a.getZ();
		return normalizeDegrees(Math.toDegrees(Math.atan2(dz, dx)));
	}

	/**
	 * 计算「最大半径圆弧」在未绑定端点处的切向角。
	 * <p>
	 * 固定端点 F 的切向为 fixedAngle，求过 free 点且在该处与方向相切的圆（唯一解），
	 * 返回该圆在 free 点处的切向角，用于平滑衔接。
	 */
	public static double maxRadiusTangentAngle(BlockPos fixed, double fixedAngle, BlockPos free) {
		final double fx = fixed.getX(), fz = fixed.getZ();
		final double px = free.getX(), pz = free.getZ();
		final double rad = Math.toRadians(fixedAngle);
		final double dirX = Math.cos(rad);
		final double dirZ = Math.sin(rad);
		final double nX = -dirZ;
		final double nZ = dirX;

		final double dX = px - fx;
		final double dZ = pz - fz;
		final double nDotD = nX * dX + nZ * dZ;
		if (Math.abs(nDotD) < 1e-6) {
			return straightAngle(fixed, free);
		}
		final double dDotD = dX * dX + dZ * dZ;
		final double t = dDotD / (2 * nDotD);
		final double cx = fx + t * nX;
		final double cz = fz + t * nZ;

		final double rX = px - cx;
		final double rZ = pz - cz;
		double tangX = -rZ;
		double tangZ = rX;

		final double fixRadX = fx - cx;
		final double fixRadZ = fz - cz;
		final double fixTangX = -fixRadZ;
		final double fixTangZ = fixRadX;
		if (fixTangX * dirX + fixTangZ * dirZ >= 0) {
			tangX = -tangX;
			tangZ = -tangZ;
		}
		return normalizeDegrees(Math.toDegrees(Math.atan2(tangZ, tangX)));
	}

	/** 该位置是否为万向节点。 */
	public static boolean isMultiDirectionNode(World world, BlockPos pos) {
		return world.getBlockState(pos).getBlock() instanceof top.s1metro.s1mtr.block.MultiDirectionNodeBlock;
	}

	/** 读取节点方向；普通 MTR 节点读 blockstate 角度。 */
	public static double getDirectionDegrees(World world, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof MultiDirectionNodeBlockEntity node) {
			return node.getDirectionDegrees();
		}
		return BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(world.getBlockState(pos)));
	}

	/** 读取连接状态；普通 MTR 节点读 blockstate。 */
	public static boolean isConnectedAt(World world, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof MultiDirectionNodeBlockEntity node) {
			return node.isConnected();
		}
		final BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof BlockNode) {
			return state.get(BlockNode.IS_CONNECTED.data);
		}
		return false;
	}

	/** 标记端点为「已连接」：万向节点写 BE，普通 MTR 节点写 blockstate。 */
	public static void markConnected(World world, BlockPos pos) {
		if (world.getBlockEntity(pos) instanceof MultiDirectionNodeBlockEntity node) {
			node.setConnected(true);
			return;
		}
		final BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof BlockNode) {
			world.setBlockState(pos, state.with(BlockNode.IS_CONNECTED.data, true), 3);
		}
	}

	/**
	 * 服务端：以两个端点位置 + 两个角度按连接器类型构建轨道并派发到 MTR 数据。
	 * <p>
	 * 关键点是跳过 MTR 原版 {@code Rail.getAngles} 内部的 {@code Angle.fromAngle → getQuadrant}
	 * 22.5° 快照，改用 {@link AngleExtra} 生成任意精度 Angle（幻影枚举实例）。
	 * RailMath 只消费 {@code angleRadians/sin/cos/tan} 连续值，因此几何支持任意角。
	 *
	 * @return 轨道是否创建成功（false = 几何不成立）
	 */
	public static boolean createAndSendRail(
			ServerWorld serverWorld,
			BlockPos pos1, double angle1,
			BlockPos pos2, double angle2,
			RailType railType, boolean isOneWay, UUID uuid
	) {
		final Position p1 = Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(pos1));
		final Position p2 = Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(pos2));

		final double angleDifference = Math.toDegrees(Math.atan2(p2.getZ() - p1.getZ(), p2.getX() - p1.getX()));
		final double deg1 = normalizeDegrees(angle1 + (Angle.similarFacing((float) angleDifference, (float) angle1) ? 0 : 180));
		final double deg2 = normalizeDegrees(angle2 + (Angle.similarFacing((float) angleDifference, (float) angle2) ? 180 : 0));

		final ObjectArrayList<String> styles = new ObjectArrayList<>();
		styles.add("default");

		final Rail rail = buildRail(p1, AngleExtra.fromDegrees(deg1), p2, AngleExtra.fromDegrees(deg2),
				styles, railType, isOneWay);
		if (rail == null || !rail.isValid()) {
			return false;
		}
		PacketUpdateData.sendDirectlyToServerRail(serverWorld, rail);
		return true;
	}

	private static Rail buildRail(Position p1, Angle a1, Position p2, Angle a2,
								  ObjectArrayList<String> styles, RailType railType, boolean isOneWay) {
		final Rail.Shape shape = railType.railShape;
		if (railType == RailType.PLATFORM) {
			return Rail.newPlatformRail(p1, a1, p2, a2, shape, 0, styles, TransportMode.TRAIN);
		}
		if (railType == RailType.SIDING) {
			return Rail.newSidingRail(p1, a1, p2, a2, shape, 0, styles, TransportMode.TRAIN);
		}
		if (railType == RailType.TURN_BACK) {
			return Rail.newTurnBackRail(p1, a1, p2, a2, shape, 0, styles, TransportMode.TRAIN);
		}
		return Rail.newRail(
				p1, a1, p2, a2,
				shape, 0, styles,
				isOneWay ? 0 : railType.speedLimit, railType.speedLimit,
				false, false, railType.canAccelerate,
				railType == RailType.RUNWAY, railType.hasSignal,
				TransportMode.TRAIN
		);
	}
}
