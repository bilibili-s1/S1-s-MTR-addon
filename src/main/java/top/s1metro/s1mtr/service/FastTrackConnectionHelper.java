package top.s1metro.s1mtr.service;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.packet.PacketUpdateData;
import top.s1metro.s1mtr.client.builder.CompositeBuilder;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;
import top.s1metro.s1mtr.item.ItemFastTrackBuilder;

/**
 * 快速建造轨道工具的服务端连接逻辑。
 * <p>
 * 当玩家放置新的轨道节点方块、且副手持有配置好的快速建造工具时，
 * 自动从"上一个节点"创建一条轨道（使用配置的限速与样式），并进行复合放样，
 * 随后把当前节点记为新的"上一个节点"。
 */
public final class FastTrackConnectionHelper {

	private FastTrackConnectionHelper() {
	}

	public static void onNodePlaced(ServerPlayerEntity player, ItemStack stack, BlockPos pos) {
		final long nx = pos.getX();
		final long ny = pos.getY();
		final long nz = pos.getZ();

		if (ItemFastTrackBuilder.hasPreviousNode(stack)) {
			final long[] prev = ItemFastTrackBuilder.getPreviousNode(stack);
			final ServerWorld serverWorld = new org.mtr.mapping.holder.ServerWorld(player.getServerWorld());
			connectAndLay(serverWorld, prev, new long[]{nx, ny, nz}, stack);
		}

		ItemFastTrackBuilder.setPreviousNode(stack, nx, ny, nz);
	}

	private static void connectAndLay(ServerWorld serverWorld, long[] prev, long[] current, ItemStack stack) {
		final Position position1 = new Position(prev[0], prev[1], prev[2]);
		final Position position2 = new Position(current[0], current[1], current[2]);
		if (position1.equals(position2)) {
			return;
		}

		// 直线方位角 (MTR 角度: 0=东, 逆时针为正)
		float facing1 = (float) Math.toDegrees(Math.atan2(position2.getZ() - position1.getZ(), position2.getX() - position1.getX()));
		float facing2 = facing1 + 180;
		facing1 = ((facing1 % 360) + 360) % 360;
		facing2 = ((facing2 % 360) + 360) % 360;

		final Angle angle1 = Angle.fromAngle(facing1);
		final Angle angle2 = Angle.fromAngle(facing2);

		final long speed = Math.max(1, ItemFastTrackBuilder.getSpeed(stack));
		String style = ItemFastTrackBuilder.getStyle(stack);
		if (style.isEmpty()) {
			style = "default";
		}
		final ObjectArrayList<String> styles = new ObjectArrayList<>();
		styles.add(style);

		final Rail rail = Rail.newRail(
				position1, angle1, position2, angle2,
				Rail.Shape.QUADRATIC, 0, styles,
				speed, speed, false, false, true, false, false,
				TransportMode.TRAIN);

		if (!rail.isValid()) {
			return;
		}

		PacketUpdateData.sendDirectlyToServerRail(serverWorld, rail);

		// 更新两端节点方块为"已连接"状态, 与 MTR 原版连接逻辑保持一致
		updateNodeConnected(serverWorld, position1.getX(), position1.getY(), position1.getZ());
		updateNodeConnected(serverWorld, position2.getX(), position2.getY(), position2.getZ());

		// 仅当配置了复合构建剖面时才放样
		if (stack.getOrCreateNbt().contains(ItemFastTrackBuilder.KEY_SCHEDULE)) {
			final CompositeLayerSchedule schedule = ItemFastTrackBuilder.getSchedule(stack);
			if (schedule != null && schedule.size() > 0) {
				CompositeBuilder.build(serverWorld, rail, schedule);
			}
		}
	}

	/**
	 * 把指定位置的轨道节点方块更新为"已连接"状态 (IS_CONNECTED = true)。
	 */
	public static void updateNodeConnected(ServerWorld serverWorld, long x, long y, long z) {
		final org.mtr.mapping.holder.BlockPos pos = new org.mtr.mapping.holder.BlockPos((int) x, (int) y, (int) z);
		final BlockState state = serverWorld.getBlockState(pos);
		if (state != null && state.getBlock().data instanceof BlockNode) {
			final BlockState connected = state.with(new Property(BlockNode.IS_CONNECTED.data), Boolean.TRUE);
			serverWorld.setBlockState(pos, connected, 3);
		}
	}
}
