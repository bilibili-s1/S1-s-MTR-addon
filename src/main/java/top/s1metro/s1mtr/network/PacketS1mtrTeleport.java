package top.s1metro.s1mtr.network;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;

/**
 * 客户端 → 服务端:请求将执行者传送到指定坐标。
 * <p>
 * 用于"传送到轨道另一端"按钮。服务端校验玩家权限等级 ≥ 2 后执行传送。
 * 传送时 Y 坐标会 +1,避免卡进地面。
 */
public class PacketS1mtrTeleport extends PacketHandler {

	private final double x, y, z;

	public PacketS1mtrTeleport(PacketBufferReceiver receiver) {
		x = receiver.readDouble();
		y = receiver.readDouble();
		z = receiver.readDouble();
	}

	public PacketS1mtrTeleport(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeDouble(x);
		sender.writeDouble(y);
		sender.writeDouble(z);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		player.teleport(player.getServerWorld(), x + 0.5, y + 1, z + 0.5, player.getYaw(1), player.getPitch(1));
	}
}
