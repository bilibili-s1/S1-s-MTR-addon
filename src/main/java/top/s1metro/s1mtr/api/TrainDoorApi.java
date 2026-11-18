package top.s1metro.s1mtr.api;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.Vehicle;
import org.mtr.core.tool.Vector;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.PersistentVehicleData;
import org.mtr.mod.data.VehicleExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 列车开门进度公开 API（返回 0~100 的百分比）。
 * <p>
 * 数据来源是 MTR 客户端的 {@link PersistentVehicleData#getDoorValue()}（0~1 连续动画值），
 * 因此与玩家看到的车门开闭动画完全一致，而非{@code doorCooldown} 之类的计时器。
 * <p>
 * 这是客户端 API（依赖 {@link MinecraftClientData}），只能在客户端环境调用。
 */
public final class TrainDoorApi {

	private TrainDoorApi() {
	}

	/** 指定列车的开门百分比（0=全关，100=全开）；列车为空或无数据时返回 0。 */
	public static float getDoorPercent(VehicleExtension vehicle) {
		if (vehicle == null) {
			return 0F;
		}
		final PersistentVehicleData data = vehicle.persistentVehicleData;
		if (data == null) {
			return 0F;
		}
		final double value = data.getDoorValue();
		if (!Double.isFinite(value) || value <= 0) {
			return 0F;
		}
		return (float) Math.min(100.0, Math.max(0.0, value * 100.0));
	}

	/** 指定列车 ID 的开门百分比；找不到返回 0。 */
	public static float getDoorPercent(long vehicleId) {
		final MinecraftClientData data = clientData();
		if (data == null) {
			return 0F;
		}
		final PersistentVehicleData persistent = data.vehicleIdToPersistentVehicleData.get(vehicleId);
		if (persistent == null) {
			return 0F;
		}
		final double value = persistent.getDoorValue();
		return !Double.isFinite(value) || value <= 0 ? 0F : (float) Math.min(100.0, value * 100.0);
	}

	/** 离玩家最近的列车的开门百分比；附近无列车返回 0。 */
	public static float getNearestDoorPercent() {
		final VehicleExtension nearest = getNearestTrain();
		return nearest == null ? 0F : getDoorPercent(nearest);
	}

	/** 离玩家最近的列车（车头到玩家的 3D 距离最小）；附近无列车返回 null。 */
	public static VehicleExtension getNearestTrain() {
		final MinecraftClientData data = clientData();
		final MinecraftClient client = MinecraftClient.getInstance();
		if (data == null || client.player == null || client.world == null) {
			return null;
		}
		final Vec3d playerPos = client.player.getPos();
		VehicleExtension best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (VehicleExtension vehicle : data.vehicles) {
			final Vector head = vehicle.getHeadPosition();
			if (head == null) {
				continue;
			}
			final double dx = head.x() - playerPos.x;
			final double dy = head.y() - playerPos.y;
			final double dz = head.z() - playerPos.z;
			final double distSq = dx * dx + dy * dy + dz * dz;
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				best = vehicle;
			}
		}
		return best;
	}

	/** 某车站内所有列车的开门百分比列表（按车辆 ID 匹配站台所属车站）。 */
	public static List<Float> getDoorPercentsAtStation(long stationId) {
		final MinecraftClientData data = clientData();
		if (data == null) {
			return Collections.emptyList();
		}
		final List<Float> result = new ArrayList<>();
		for (VehicleExtension vehicle : data.vehicles) {
			final long platformId = vehicle.vehicleExtraData.getThisPlatformId();
			if (platformId != 0 && stationMatchesPlatform(data, stationId, platformId)) {
				result.add(getDoorPercent(vehicle));
			}
		}
		return result;
	}

	/** 某站台内所有列车的开门百分比列表。 */
	public static List<Float> getDoorPercentsAtPlatform(long platformId) {
		final MinecraftClientData data = clientData();
		if (data == null) {
			return Collections.emptyList();
		}
		final List<Float> result = new ArrayList<>();
		for (VehicleExtension vehicle : data.vehicles) {
			if (vehicle.vehicleExtraData.getThisPlatformId() == platformId) {
				result.add(getDoorPercent(vehicle));
			}
		}
		return result;
	}

	private static boolean stationMatchesPlatform(MinecraftClientData data, long stationId, long platformId) {
		try {
			for (org.mtr.core.data.Platform platform : data.platforms) {
				if (platform.getId() != platformId) {
					continue;
				}
				// Platform 与 Station 的关联未在公开 API 暴露，反射读取可能的 stationId 字段
				for (java.lang.reflect.Field f : org.mtr.core.data.SavedRailBase.class.getDeclaredFields()) {
					if (f.getName().toLowerCase().contains("station") && f.getType() == long.class) {
						f.setAccessible(true);
						return f.getLong(platform) == stationId;
					}
				}
				// 兜底：无 stationId 字段时不误判（返回 false）
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private static MinecraftClientData clientData() {
		try {
			return MinecraftClientData.getInstance();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
