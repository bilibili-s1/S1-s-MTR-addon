package top.s1metro.s1mtr.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.packet.PacketUpdateData;
import top.s1metro.s1mtr.item.ItemNodeCopier;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 轨道节点复制工具的"粘贴"逻辑(服务端)。
 * <p>
 * 在目标位置放置一个 MTR 轨道节点方块,并用复制时保存的轨道属性
 * 把新节点与各原连接节点自动连接。
 */
public final class PacketS1mtrPasteNode {

	private static final Gson GSON = new Gson();

	private PacketS1mtrPasteNode() {
	}

	/** 服务端处理:放置节点并自动连接。 */
	public static void handle(ServerPlayerEntity player, net.minecraft.item.ItemStack stack, BlockPos newPos) {
		if (stack == null || !(stack.getItem() instanceof ItemNodeCopier)) {
			return;
		}
		final String json = ItemNodeCopier.getCopiedData(stack);
		if (json.isEmpty()) {
			return;
		}

		final JsonObject root;
		try {
			root = GSON.fromJson(json, JsonObject.class);
		} catch (Exception e) {
			return;
		}
		if (root == null || !root.has("connections")) {
			return;
		}

		final long originX = root.get("originX").getAsLong();
		final long originY = root.get("originY").getAsLong();
		final long originZ = root.get("originZ").getAsLong();
		final JsonArray connections = root.getAsJsonArray("connections");
		if (connections.size() == 0) {
			player.sendMessage(net.minecraft.text.Text.translatable("item.s1mtraddon.node_copier.no_connections"), true);
			return;
		}

		final ServerWorld serverWorld = new ServerWorld(player.getServerWorld());
		final Position newPosition = new Position(newPos.getX(), newPos.getY(), newPos.getZ());

		// 放置轨道节点方块:复用原节点的 BlockNode 实例(保证 transportMode 一致)
		final net.minecraft.block.BlockState originState = player.getServerWorld().getBlockState(
				new BlockPos((int) originX, (int) originY, (int) originZ));
		final net.minecraft.block.Block originBlock = originState.getBlock();
		if (!(originBlock instanceof BlockNode)) {
			player.sendMessage(net.minecraft.text.Text.translatable("item.s1mtraddon.node_copier.fail"), true);
			return;
		}
		player.getServerWorld().setBlockState(newPos, originBlock.getDefaultState(), 3);

		int connectedCount = 0;
		for (int i = 0; i < connections.size(); i++) {
			final JsonObject conn = connections.get(i).getAsJsonObject();
			final Position other = new Position(
					conn.get("x").getAsLong(),
					conn.get("y").getAsLong(),
					conn.get("z").getAsLong());

			final Angle savedAngle1 = Angle.fromAngle(conn.get("angle1").getAsFloat());
			final Angle savedAngle2 = Angle.fromAngle(conn.get("angle2").getAsFloat());

			// 用新节点位置 + 保存的朝向重算两端真实角度
			final ObjectObjectImmutablePair<Angle, Angle> angles =
					Rail.getAngles(newPosition, savedAngle1.angleDegrees, other, savedAngle2.angleDegrees);
			if (angles == null) {
				continue;
			}

			final Rail.Shape shape = Rail.Shape.valueOf(conn.get("shape").getAsString());
			final double radius = conn.get("radius").getAsDouble();
			final long speed1 = conn.get("speed1").getAsLong();
			final long speed2 = conn.get("speed2").getAsLong();
			final boolean isPlatform = conn.get("platform").getAsBoolean();
			final boolean isSiding = conn.get("siding").getAsBoolean();
			final boolean canAccelerate = conn.get("canAccelerate").getAsBoolean();
			final boolean canTurnBack = conn.get("canTurnBack").getAsBoolean();
			final boolean canConnectRemotely = conn.get("canConnectRemotely").getAsBoolean();
			final TransportMode transportMode = TransportMode.valueOf(conn.get("transportMode").getAsString());

			final ObjectArrayList<String> styles = new ObjectArrayList<>();
			final JsonArray styleArr = conn.getAsJsonArray("styles");
			for (int j = 0; j < styleArr.size(); j++) {
				styles.add(styleArr.get(j).getAsString());
			}
			if (styles.isEmpty()) {
				styles.add("default");
			}

			// 速度:保存的 speed1/speed2 决定单向(speed2==0 表示单向)
			final Rail rail = Rail.newRail(
					newPosition, angles.left(), other, angles.right(),
					shape, radius, styles,
					speed1, speed2, isPlatform, isSiding,
					canAccelerate, canTurnBack, canConnectRemotely, transportMode);

			if (rail != null && rail.isValid()) {
				PacketUpdateData.sendDirectlyToServerRail(serverWorld, rail);
				markConnected(serverWorld, newPos);
				markConnected(serverWorld, new BlockPos((int) other.getX(), (int) other.getY(), (int) other.getZ()));
				connectedCount++;
			}
		}

		ItemNodeCopier.clearCopiedData(stack);
		player.sendMessage(
				net.minecraft.text.Text.translatable("item.s1mtraddon.node_copier.pasted", connectedCount),
				true);
	}

	private static void markConnected(ServerWorld world, BlockPos pos) {
		final org.mtr.mapping.holder.BlockPos holderPos = new org.mtr.mapping.holder.BlockPos(pos);
		final BlockState state = world.getBlockState(holderPos);
		if (state != null && state.getBlock().data instanceof BlockNode) {
			world.setBlockState(holderPos, state.with(new Property(BlockNode.IS_CONNECTED.data), Boolean.TRUE), 3);
		}
	}
}
