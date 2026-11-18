package top.s1metro.s1mtr.service;

import org.mtr.core.data.Data;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Siding;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.tool.Utilities;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * 侧线「列车限速」读写工具（magic-mtr 方案）。
 * <p>
 * 写入 {@link Siding#setMaxManualSpeed(double)}，单位 m/ms。MTR 自身在
 * {@code Vehicle.simulateMoving} 与 {@code Siding.generatePathDistancesAndTimeSegments}
 * 都会取 min(轨道限速, 列车限速)，因此运行与时刻表一致，不会出现追点/倒数器错乱。
 * <p>
 * 0 或负值表示不限速（内部用 1000 km/h 近似无限）。
 */
public final class TrainSpeedLimit {

	public static final double NO_LIMIT_KMH = 1000;

	private TrainSpeedLimit() {
	}

	/** 从客户端数据里找包含指定轨道的侧线。 */
	public static Siding findSiding(Rail rail) {
		if (rail == null) {
			return null;
		}
		try {
			final Data data = MinecraftClientData.getInstance();
			for (Siding siding : data.sidings) {
				if (sidingContainsRail(siding, rail)) {
					return siding;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static boolean sidingContainsRail(Siding siding, Rail rail) {
		// Siding 有三条 PathData 列表；PathData.getRail() 直接给出轨道
		for (final String fieldName : new String[]{"pathMainRoute", "pathSidingToMainRoute", "pathMainRouteToSiding"}) {
			try {
				final Field field = findField(Siding.class, fieldName);
				if (field == null) {
					continue;
				}
				field.setAccessible(true);
				final Object value = field.get(siding);
				if (value instanceof Iterable<?> iterable) {
					for (Object obj : iterable) {
						if (obj instanceof org.mtr.core.data.PathData pathData && pathData.getRail() == rail) {
							return true;
						}
					}
				}
			} catch (Exception ignored) {
			}
		}
		return false;
	}

	/** 读取侧线列车限速（km/h），来自本模组独立存储；无限制返回 0。 */
	public static double getKmh(Rail rail) {
		final Siding siding = findSiding(rail);
		if (siding == null) {
			return 0;
		}
		return SidingSpeedStore.get(siding.getId());
	}

	/** 设置侧线列车限速并同步到服务端。 */
	public static void setKmh(Rail rail, double kmh) {
		final Siding siding = findSiding(rail);
		if (siding == null) {
			return;
		}
		// 独立 UI 存储（可编辑，不与手动限速同步）
		SidingSpeedStore.set(siding.getId(), kmh);
		// 执行层同步
		final double speedKmh = kmh > 0 ? Math.min(kmh, NO_LIMIT_KMH) : NO_LIMIT_KMH;
		siding.setMaxManualSpeed(Utilities.kilometersPerHourToMetersPerMillisecond(speedKmh));
		try {
			final UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getDashboardInstance());
			request.addSiding(siding);
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
		} catch (Exception ignored) {
		}
	}

	private static Field findField(Class<?> startClass, String fieldName) {
		Class<?> clazz = startClass;
		while (clazz != null) {
			try {
				return clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}
}
