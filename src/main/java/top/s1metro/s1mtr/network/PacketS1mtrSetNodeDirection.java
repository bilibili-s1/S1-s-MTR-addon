package top.s1metro.s1mtr.network;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlockEntity;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * 客户端 → 服务端：设置万向节点的方向与绑定状态。
 * <p>
 * 配置界面即时保存；服务端是 {@code connected} 与方向的权威来源，客户端同时本地更新以获得即时反馈。
 */
public class PacketS1mtrSetNodeDirection extends PacketHandler {

	private final long x, y, z;
	private final double direction;
	private final boolean bonded;

	public PacketS1mtrSetNodeDirection(PacketBufferReceiver receiver) {
		x = receiver.readLong();
		y = receiver.readLong();
		z = receiver.readLong();
		direction = receiver.readDouble();
		bonded = receiver.readBoolean();
	}

	public PacketS1mtrSetNodeDirection(BlockPos pos, double direction, boolean bonded) {
		this.x = pos.getX();
		this.y = pos.getY();
		this.z = pos.getZ();
		this.direction = direction;
		this.bonded = bonded;
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeLong(x);
		sender.writeLong(y);
		sender.writeLong(z);
		sender.writeDouble(direction);
		sender.writeBoolean(bonded);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		final net.minecraft.server.world.ServerWorld world = player.getServerWorld().data;
		if (world == null) {
			return;
		}
		final BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
		if (!world.isChunkLoaded(pos)) {
			return;
		}
		final BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof MultiDirectionNodeBlockEntity node) {
			node.setDirectionUnbound(direction);
			node.setRotationBonded(bonded);
		}
	}
}
