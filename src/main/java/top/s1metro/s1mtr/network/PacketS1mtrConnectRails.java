package top.s1metro.s1mtr.network;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.packet.PacketUpdateData;

/**
 * 客户端 → 服务端:请求在两个位置之间创建一条轨道。
 * <p>
 * 客户端发送两个节点坐标、轨道类型与限速(km/h)。服务端据此计算直线朝向并创建对应类型的
 * 轨道（普通/侧线/站台/掉头），轨道样式带 "default"，通过
 * {@link PacketUpdateData#sendDirectlyToServerRail} 保存到存档。
 * 服务端校验玩家权限等级 ≥ 2 后执行。
 */
public class PacketS1mtrConnectRails extends PacketHandler {

	/** 普通轨道 */
	public static final int TYPE_NORMAL = 0;
	/** 侧线轨道 */
	public static final int TYPE_SIDING = 1;
	/** 站台轨道 */
	public static final int TYPE_PLATFORM = 2;
	/** 掉头轨道 */
	public static final int TYPE_TURNBACK = 3;

	private final long position1X, position1Y, position1Z;
	private final long position2X, position2Y, position2Z;
	private final long speedLimit;
	private final int railType;

	public PacketS1mtrConnectRails(PacketBufferReceiver receiver) {
		position1X = receiver.readLong();
		position1Y = receiver.readLong();
		position1Z = receiver.readLong();
		position2X = receiver.readLong();
		position2Y = receiver.readLong();
		position2Z = receiver.readLong();
		speedLimit = receiver.readLong();
		railType = receiver.readInt();
	}

	public PacketS1mtrConnectRails(Position position1, Position position2, long speedLimit, int railType) {
		position1X = position1.getX();
		position1Y = position1.getY();
		position1Z = position1.getZ();
		position2X = position2.getX();
		position2Y = position2.getY();
		position2Z = position2.getZ();
		this.speedLimit = Math.max(1, speedLimit);
		this.railType = railType;
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeLong(position1X);
		sender.writeLong(position1Y);
		sender.writeLong(position1Z);
		sender.writeLong(position2X);
		sender.writeLong(position2Y);
		sender.writeLong(position2Z);
		sender.writeLong(speedLimit);
		sender.writeInt(railType);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		if (!player.hasPermissionLevel(2)) {
			return;
		}
		final Position position1 = new Position(position1X, position1Y, position1Z);
		final Position position2 = new Position(position2X, position2Y, position2Z);
		if (position1.equals(position2)) {
			return;
		}

		// 基于两个位置计算直线方位角 (MTR 角度: 0=东, 逆时针为正)
		float facing1 = (float) Math.toDegrees(Math.atan2(position2.getZ() - position1.getZ(), position2.getX() - position1.getX()));
		float facing2 = facing1 + 180;
		facing1 = ((facing1 % 360) + 360) % 360;
		facing2 = ((facing2 % 360) + 360) % 360;

		final Angle angle1 = Angle.fromAngle(facing1);
		final Angle angle2 = Angle.fromAngle(facing2);

		// 样式必须带 "default", 否则轨道不会在地图上显示
		final ObjectArrayList<String> styles = new ObjectArrayList<>();
		styles.add("default");

		final Rail rail;
		switch (railType) {
			case TYPE_SIDING:
				rail = Rail.newSidingRail(position1, angle1, position2, angle2, Rail.Shape.QUADRATIC, 0, styles, TransportMode.TRAIN);
				break;
			case TYPE_PLATFORM:
				rail = Rail.newPlatformRail(position1, angle1, position2, angle2, Rail.Shape.QUADRATIC, 0, styles, TransportMode.TRAIN);
				break;
			case TYPE_TURNBACK:
				rail = Rail.newTurnBackRail(position1, angle1, position2, angle2, Rail.Shape.QUADRATIC, 0, styles, TransportMode.TRAIN);
				break;
			default:
				rail = Rail.newRail(position1, angle1, position2, angle2, Rail.Shape.QUADRATIC, 0,
						styles, speedLimit, speedLimit, false, false, true, false, false, TransportMode.TRAIN);
				break;
		}

		if (rail.isValid()) {
			final org.mtr.mapping.holder.ServerWorld serverWorld = player.getServerWorld();
			PacketUpdateData.sendDirectlyToServerRail(serverWorld, rail);
			// 更新两端节点方块为"已连接"状态
			top.s1metro.s1mtr.service.FastTrackConnectionHelper.updateNodeConnected(serverWorld,
					position1.getX(), position1.getY(), position1.getZ());
			top.s1metro.s1mtr.service.FastTrackConnectionHelper.updateNodeConnected(serverWorld,
					position2.getX(), position2.getY(), position2.getZ());
		}
	}
}
