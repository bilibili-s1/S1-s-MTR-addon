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
 * 连接模式:在目标位置放置一个新节点,并用复制时保存的轨道属性把新节点与各原连接节点连接。
 * <p>
 * 完全复制模式:原节点放粘贴点,每个相连节点按<b>相对位置</b>放置对应新节点
 * (位置 = 粘贴点 + 相对偏移,朝向保留),并把这些新节点与粘贴点的新节点连接。
 * 适合快速建设多线铁路。
 */
public final class PacketS1mtrPasteNode {

	private static final Gson GSON = new Gson();

	private PacketS1mtrPasteNode() {
	}

	/** 服务端处理:放置节点并自动连接。 */
	public static void handle(ServerPlayerEntity player, net.minecraft.item.ItemStack stack, BlockPos newPos, int mode) {
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

		final boolean facing = root.has("facing") ? root.get("facing").getAsBoolean() : true;
		final boolean is22_5 = root.has("is22_5") ? root.get("is22_5").getAsBoolean() : false;
		final boolean is45 = root.has("is45") ? root.get("is45").getAsBoolean() : false;

		// 复用原节点的 BlockNode 实例(保证 transportMode 一致)
		final net.minecraft.block.BlockState originState = player.getServerWorld().getBlockState(
				new BlockPos((int) originX, (int) originY, (int) originZ));
		final net.minecraft.block.Block originBlock = originState.getBlock();
		if (!(originBlock instanceof BlockNode)) {
			player.sendMessage(net.minecraft.text.Text.translatable("item.s1mtraddon.node_copier.fail"), true);
			return;
		}

		// 放置原节点(粘贴点),保持原朝向
		player.getServerWorld().setBlockState(newPos,
				applyNodeProps(originBlock.getDefaultState(), facing, is22_5, is45), 3);

		int connectedCount = 0;
		for (int i = 0; i < connections.size(); i++) {
			final JsonObject conn = connections.get(i).getAsJsonObject();
			final Position other = new Position(
					conn.get("x").getAsLong(),
					conn.get("y").getAsLong(),
					conn.get("z").getAsLong());

			final Angle savedAngle1 = Angle.fromAngle(conn.get("angle1").getAsFloat());
			final Angle savedAngle2 = Angle.fromAngle(conn.get("angle2").getAsFloat());

			// 另一端节点位置:连接模式用原节点,完全复制模式用相对位置的新节点
			final Position targetPos;
			if (mode == ItemNodeCopier.MODE_COPY_ALL) {
				targetPos = new Position(
						newPosition.getX() + (other.getX() - originX),
						newPosition.getY() + (other.getY() - originY),
						newPosition.getZ() + (other.getZ() - originZ));

				// 在相对位置放置对应的新节点(保留其朝向)
				final boolean ofacing = conn.has("otherFacing") ? conn.get("otherFacing").getAsBoolean() : true;
				final boolean ois22_5 = conn.has("otherIs22_5") ? conn.get("otherIs22_5").getAsBoolean() : false;
				final boolean ois45 = conn.has("otherIs45") ? conn.get("otherIs45").getAsBoolean() : false;
				player.getServerWorld().setBlockState(
						new BlockPos((int) targetPos.getX(), (int) targetPos.getY(), (int) targetPos.getZ()),
						applyNodeProps(originBlock.getDefaultState(), ofacing, ois22_5, ois45), 3);
			} else {
				targetPos = other;
			}

			// 用两端真实朝向重算角度
			final ObjectObjectImmutablePair<Angle, Angle> angles =
					Rail.getAngles(newPosition, savedAngle1.angleDegrees, targetPos, savedAngle2.angleDegrees);
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

			final Rail rail = Rail.newRail(
					newPosition, angles.left(), targetPos, angles.right(),
					shape, radius, styles,
					speed1, speed2, isPlatform, isSiding,
					canAccelerate, canTurnBack, canConnectRemotely, transportMode);

			if (rail != null && rail.isValid()) {
				PacketUpdateData.sendDirectlyToServerRail(serverWorld, rail);
				markConnected(serverWorld, newPos);
				markConnected(serverWorld, new BlockPos((int) targetPos.getX(), (int) targetPos.getY(), (int) targetPos.getZ()));
				connectedCount++;
			}
		}

		ItemNodeCopier.clearCopiedData(stack);
		player.sendMessage(
				net.minecraft.text.Text.translatable("item.s1mtraddon.node_copier.pasted", connectedCount),
				true);
	}

	/** 设置 BlockNode 的三个朝向属性。 */
	private static net.minecraft.block.BlockState applyNodeProps(
			net.minecraft.block.BlockState state, boolean facing, boolean is22_5, boolean is45) {
		net.minecraft.block.BlockState result = state;
		result = applyNodeProp(result, org.mtr.mod.block.BlockNode.FACING, facing);
		result = applyNodeProp(result, org.mtr.mod.block.BlockNode.IS_22_5, is22_5);
		result = applyNodeProp(result, org.mtr.mod.block.BlockNode.IS_45, is45);
		return result;
	}

	/** 设置 BlockNode 的 BooleanProperty 属性值。 */
	private static net.minecraft.block.BlockState applyNodeProp(
			net.minecraft.block.BlockState state, org.mtr.mapping.holder.BooleanProperty prop, boolean value) {
		try {
			final net.minecraft.state.property.Property<Boolean> p =
					(net.minecraft.state.property.Property<Boolean>) prop.data;
			if (state.contains(p)) {
				return state.with(p, value);
			}
		} catch (Exception ignored) {
		}
		return state;
	}

	private static void markConnected(ServerWorld world, BlockPos pos) {
		final org.mtr.mapping.holder.BlockPos holderPos = new org.mtr.mapping.holder.BlockPos(pos);
		final BlockState state = world.getBlockState(holderPos);
		if (state != null && state.getBlock().data instanceof BlockNode) {
			world.setBlockState(holderPos, state.with(new Property(BlockNode.IS_CONNECTED.data), Boolean.TRUE), 3);
		}
	}
}
