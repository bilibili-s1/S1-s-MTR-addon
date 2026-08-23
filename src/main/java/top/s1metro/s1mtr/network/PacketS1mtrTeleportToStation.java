package top.s1metro.s1mtr.network;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;

/**
 * 客户端 → 服务端:请求传送玩家到指定站点中心坐标。
 * <p>
 * 由车站传送器工具的确认菜单触发。服务端校验权限等级 ≥ 2 后执行传送。
 * 传送时 Y 轴 +1 避免卡进地面,且 X/Z +0.5 传送到方块中心。
 */
public class PacketS1mtrTeleportToStation extends PacketHandler {

	private final double x, y, z;

	public PacketS1mtrTeleportToStation(PacketBufferReceiver receiver) {
		x = receiver.readDouble();
		y = receiver.readDouble();
		z = receiver.readDouble();
	}

	public PacketS1mtrTeleportToStation(double x, double y, double z) {
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
		// 若 y 无效 (未获取到有效高度), 保持玩家当前高度 (相当于命令中的 ~ 相对坐标)
		final double finalY = Double.isNaN(y) ? player.getY() : y;
		player.teleport(player.getServerWorld(), x + 0.5, finalY + 1, z + 0.5, player.getYaw(1), player.getPitch(1));
	}
}
