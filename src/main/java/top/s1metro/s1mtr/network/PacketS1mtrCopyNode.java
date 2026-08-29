package top.s1metro.s1mtr.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import top.s1metro.s1mtr.item.ItemNodeCopier;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 轨道节点复制工具的"复制"逻辑。
 * <p>
 * <b>客户端</b>:读取指定轨道节点的所有连接轨道属性,序列化后发往服务端;
 * <b>服务端</b>:把序列化数据写入物品 NBT(随后客户端按 NBT 切换贴图 2)。
 */
public final class PacketS1mtrCopyNode {

	private static final Gson GSON = new Gson();

	private PacketS1mtrCopyNode() {
	}

	/**
	 * 客户端:读取节点连接并返回序列化 JSON 字符串。
	 * 调用方负责把结果发往服务端。
	 *
	 * @param pos 被右键的轨道节点坐标
	 * @return 序列化的连接数据 JSON;无连接或失败返回 null
	 */
	public static String collectConnections(BlockPos pos) {
		final Position origin = new Position(pos.getX(), pos.getY(), pos.getZ());
		final JsonObject root = new JsonObject();
		final JsonArray connections = new JsonArray();

		final Map<Position, Map<Position, Rail>> positionsToRail = getClientPositionsToRail();
		if (positionsToRail != null) {
			final Map<Position, Rail> connected = positionsToRail.get(origin);
			if (connected != null) {
				for (Map.Entry<Position, Rail> entry : connected.entrySet()) {
					final JsonObject conn = serializeRail(entry.getKey(), entry.getValue());
					if (conn != null) {
						connections.add(conn);
					}
				}
			}
		}

		root.addProperty("originX", pos.getX());
		root.addProperty("originY", pos.getY());
		root.addProperty("originZ", pos.getZ());
		root.add("connections", connections);

		return GSON.toJson(root);
	}

	/** 服务端:把连接数据写入玩家手持物品 NBT。 */
	public static void handle(ServerPlayerEntity player, net.minecraft.item.ItemStack stack, String json) {
		if (stack == null || !(stack.getItem() instanceof ItemNodeCopier) || json == null) {
			return;
		}
		ItemNodeCopier.setCopiedData(stack, json);

		int count = 0;
		try {
			final JsonObject root = GSON.fromJson(json, JsonObject.class);
			count = root.has("connections") ? root.getAsJsonArray("connections").size() : 0;
		} catch (Exception ignored) {
		}
		player.sendMessage(
				net.minecraft.text.Text.translatable(
						count > 0 ? "item.s1mtraddon.node_copier.copied" : "item.s1mtraddon.node_copier.no_connections",
						count),
				true);
	}

	private static JsonObject serializeRail(Position other, Rail rail) {
		final RailSchemaAccessor acc = (RailSchemaAccessor) (Object) rail;
		final JsonObject obj = new JsonObject();
		obj.addProperty("x", other.getX());
		obj.addProperty("y", other.getY());
		obj.addProperty("z", other.getZ());
		obj.addProperty("angle1", acc.s1mtr$getAngle1().angleDegrees);
		obj.addProperty("angle2", acc.s1mtr$getAngle2().angleDegrees);
		obj.addProperty("shape", acc.s1mtr$getShape().name());
		obj.addProperty("radius", acc.s1mtr$getVerticalRadius());
		obj.addProperty("speed1", acc.s1mtr$getSpeedLimit1());
		obj.addProperty("speed2", acc.s1mtr$getSpeedLimit2());
		obj.addProperty("platform", acc.s1mtr$isPlatform());
		obj.addProperty("siding", acc.s1mtr$isSiding());
		obj.addProperty("canAccelerate", acc.s1mtr$canAccelerate());
		obj.addProperty("canTurnBack", acc.s1mtr$canTurnBack());
		obj.addProperty("canConnectRemotely", acc.s1mtr$canConnectRemotely());
		obj.addProperty("canHaveSignal", acc.s1mtr$canHaveSignal());
		obj.addProperty("transportMode", acc.s1mtr$getTransportMode().name());
		final ObjectArrayList<String> styles = acc.s1mtr$getStyles();
		final JsonArray styleArr = new JsonArray();
		for (String s : styles) {
			styleArr.add(s);
		}
		obj.add("styles", styleArr);
		return obj;
	}

	/** 反射获取客户端 {@code MinecraftClientData.positionsToRail} 字段。 */
	@SuppressWarnings("unchecked")
	private static Map<Position, Map<Position, Rail>> getClientPositionsToRail() {
		try {
			final org.mtr.mod.client.MinecraftClientData data = org.mtr.mod.client.MinecraftClientData.getInstance();
			final Field field = findField(Data.class, "positionsToRail");
			if (field == null) {
				return null;
			}
			field.setAccessible(true);
			return (Map<Position, Map<Position, Rail>>) field.get(data);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Field findField(Class<?> clazz, String name) {
		while (clazz != null) {
			try {
				return clazz.getDeclaredField(name);
			} catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}
}
