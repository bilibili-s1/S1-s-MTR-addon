package top.s1metro.s1mtr.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;

/** Rail data transfer helper (serialize / deserialize). */
public final class RailDataTransfer {

	private RailDataTransfer() {
	}

	public static JsonObject serialize(Rail rail) {
		final RailSchemaAccessor a = (RailSchemaAccessor) (Object) rail;
		final Position p1 = a.s1mtr$getPosition1();
		final Position p2 = a.s1mtr$getPosition2();
		final JsonObject obj = new JsonObject();
		obj.addProperty("x1", p1.getX());
		obj.addProperty("y1", p1.getY());
		obj.addProperty("z1", p1.getZ());
		obj.addProperty("x2", p2.getX());
		obj.addProperty("y2", p2.getY());
		obj.addProperty("z2", p2.getZ());
		obj.addProperty("angle1", a.s1mtr$getAngle1().angleDegrees);
		obj.addProperty("angle2", a.s1mtr$getAngle2().angleDegrees);
		obj.addProperty("shape", a.s1mtr$getShape().name());
		obj.addProperty("radius", a.s1mtr$getVerticalRadius());
		obj.addProperty("speed1", a.s1mtr$getSpeedLimit1());
		obj.addProperty("speed2", a.s1mtr$getSpeedLimit2());
		obj.addProperty("platform", a.s1mtr$isPlatform());
		obj.addProperty("siding", a.s1mtr$isSiding());
		obj.addProperty("canAccelerate", a.s1mtr$canAccelerate());
		obj.addProperty("canTurnBack", a.s1mtr$canTurnBack());
		obj.addProperty("canConnectRemotely", a.s1mtr$canConnectRemotely());
		obj.addProperty("transportMode", a.s1mtr$getTransportMode().name());
		final JsonArray styles = new JsonArray();
		for (String style : a.s1mtr$getStyles()) {
			styles.add(style);
		}
		obj.add("styles", styles);
		return obj;
	}

	/** 从 JSON 对象重建轨道;数据非法时返回 null。 */
	public static Rail deserialize(JsonObject obj) {
		try {
			final Position p1 = new Position(obj.get("x1").getAsLong(), obj.get("y1").getAsLong(), obj.get("z1").getAsLong());
			final Position p2 = new Position(obj.get("x2").getAsLong(), obj.get("y2").getAsLong(), obj.get("z2").getAsLong());
			final Angle angle1 = Angle.fromAngle(obj.get("angle1").getAsFloat());
			final Angle angle2 = Angle.fromAngle(obj.get("angle2").getAsFloat());

			final ObjectArrayList<String> styles = new ObjectArrayList<>();
			final JsonArray styleArray = obj.getAsJsonArray("styles");
			if (styleArray != null) {
				for (int i = 0; i < styleArray.size(); i++) {
					styles.add(styleArray.get(i).getAsString());
				}
			}
			if (styles.isEmpty()) {
				styles.add("default");
			}

			return Rail.newRail(p1, angle1, p2, angle2,
					Rail.Shape.valueOf(obj.get("shape").getAsString()),
					obj.get("radius").getAsDouble(),
					styles,
					obj.get("speed1").getAsLong(), obj.get("speed2").getAsLong(),
					obj.get("platform").getAsBoolean(), obj.get("siding").getAsBoolean(),
					obj.get("canAccelerate").getAsBoolean(), obj.get("canTurnBack").getAsBoolean(),
					obj.get("canConnectRemotely").getAsBoolean(),
					TransportMode.valueOf(obj.get("transportMode").getAsString()));
		} catch (Exception ignored) {
			return null;
		}
	}

	/**
	 * 客户端:查找某个坐标处相连的轨道(取第一条)。
	 *
	 * @return 轨道;该位置没有轨道时返回 null
	 */
	public static Rail findRailAt(net.minecraft.util.math.BlockPos pos) {
		try {
			final Position origin = new Position(pos.getX(), pos.getY(), pos.getZ());
			final java.util.Map<Position, java.util.Map<Position, Rail>> map = getClientPositionsToRail();
			if (map == null) {
				return null;
			}
			final java.util.Map<Position, Rail> connected = map.get(origin);
			if (connected == null || connected.isEmpty()) {
				return null;
			}
			return connected.values().iterator().next();
		} catch (Exception ignored) {
			return null;
		}
	}

	/** 反射获取客户端 MinecraftClientData 的 positionsToRail 字段。 */
	@SuppressWarnings("unchecked")
	private static java.util.Map<Position, java.util.Map<Position, Rail>> getClientPositionsToRail() {
		try {
			final org.mtr.mod.client.MinecraftClientData data = org.mtr.mod.client.MinecraftClientData.getInstance();
			java.lang.reflect.Field field = org.mtr.core.data.Data.class.getDeclaredField("positionsToRail");
			field.setAccessible(true);
			return (java.util.Map<Position, java.util.Map<Position, Rail>>) field.get(data);
		} catch (Exception ignored) {
			return null;
		}
	}
}
