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
import top.s1metro.s1mtr.client.builder.CompositeBuilder;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;
import top.s1metro.s1mtr.client.builder.CompositeProfile;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;

/**
 * 客户端 → 服务端:请求沿某条轨道进行复合放样。
 * <p>
 * 客户端序列化轨道的几何参数(两端位置/角度、形状、垂直半径)与分层调度表字符串,
 * 服务端据此重建 Rail 并调用 {@link CompositeBuilder#build} 执行放样。
 */
public class PacketS1mtrBuildComposite extends PacketHandler {

	private final long position1X, position1Y, position1Z;
	private final long position2X, position2Y, position2Z;
	private final String angle1, angle2;
	private final String shape;
	private final double verticalRadius;
	private final String scheduleData;

	public PacketS1mtrBuildComposite(PacketBufferReceiver receiver) {
		position1X = receiver.readLong();
		position1Y = receiver.readLong();
		position1Z = receiver.readLong();
		position2X = receiver.readLong();
		position2Y = receiver.readLong();
		position2Z = receiver.readLong();
		angle1 = receiver.readString();
		angle2 = receiver.readString();
		shape = receiver.readString();
		verticalRadius = receiver.readDouble();
		scheduleData = receiver.readString();
	}

	public PacketS1mtrBuildComposite(Rail rail, CompositeLayerSchedule schedule) {
		final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
		final Position position1 = accessor.s1mtr$getPosition1();
		final Position position2 = accessor.s1mtr$getPosition2();
		position1X = position1.getX();
		position1Y = position1.getY();
		position1Z = position1.getZ();
		position2X = position2.getX();
		position2Y = position2.getY();
		position2Z = position2.getZ();
		angle1 = accessor.s1mtr$getAngle1().name();
		angle2 = accessor.s1mtr$getAngle2().name();
		shape = accessor.s1mtr$getShape().name();
		verticalRadius = accessor.s1mtr$getVerticalRadius();
		scheduleData = schedule == null ? "" : schedule.serialize();
	}

	@Override
	public void write(PacketBufferSender sender) {
		sender.writeLong(position1X);
		sender.writeLong(position1Y);
		sender.writeLong(position1Z);
		sender.writeLong(position2X);
		sender.writeLong(position2Y);
		sender.writeLong(position2Z);
		sender.writeString(angle1);
		sender.writeString(angle2);
		sender.writeString(shape);
		sender.writeDouble(verticalRadius);
		sender.writeString(scheduleData);
	}

	@Override
	public void runServer(MinecraftServer server, ServerPlayerEntity player) {
		if (!player.hasPermissionLevel(2)) {
			return;
		}
		final Rail rail = Rail.newRail(
				new Position(position1X, position1Y, position1Z),
				Angle.valueOf(angle1),
				new Position(position2X, position2Y, position2Z),
				Angle.valueOf(angle2),
				Rail.Shape.valueOf(shape),
				verticalRadius,
				new ObjectArrayList<>(),
				0,
				0,
				false,
				false,
				true,
				false,
				false,
				TransportMode.TRAIN);
		CompositeBuilder.build(player.getServerWorld(), rail, CompositeLayerSchedule.deserialize(scheduleData));
	}
}
