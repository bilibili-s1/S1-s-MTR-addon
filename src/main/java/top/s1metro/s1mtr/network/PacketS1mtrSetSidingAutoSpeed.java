package top.s1metro.s1mtr.network;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import top.s1metro.s1mtr.service.SidingAutoSpeedLimit;

/** 客户端 → 服务端：设置侧线自动驾驶限速（km/h），与手动驾驶限速完全独立。 */
public class PacketS1mtrSetSidingAutoSpeed extends PacketHandler {

	private final long sidingId;
	private final double kmh;

	public PacketS1mtrSetSidingAutoSpeed(PacketBufferReceiver receiver) {
		sidingId = receiver.readLong();
		kmh = receiver.readDouble();
	}

	public PacketS1mtrSetSidingAutoSpeed(long sidingId, double kmh) {
		this.sidingId = sidingId;
		this.kmh = kmh;
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeLong(sidingId);
		sender.writeDouble(kmh);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		SidingAutoSpeedLimit.set(sidingId, kmh);
	}
}
