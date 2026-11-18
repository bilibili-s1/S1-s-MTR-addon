package top.s1metro.s1mtr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.s1metro.s1mtr.client.RailSpeedHelper;
import top.s1metro.s1mtr.service.VehicleDelayTracker;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Vehicle.class)
public abstract class VehicleSimulateMixin {

	private static volatile long s1mtr$currentSimDeltaMs = 0;
	private static final ConcurrentHashMap<Long, Long> DOOR_DELAY_ELAPSED_MS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<Long, Boolean> DOOR_HAS_BEEN_CLOSING = new ConcurrentHashMap<>();

	@Inject(method = "simulateStopped", at = @At("HEAD"))
	private void s1mtr$captureSimDelta(long delta, ObjectArrayList<?> list, int index, CallbackInfo ci) {
		s1mtr$currentSimDeltaMs = delta;
	}

	@WrapOperation(
			method = "simulateStopped",
			at = @At(value = "INVOKE",
					target = "Lorg/mtr/core/data/VehicleExtraData;openDoors()V")
	)
	private void s1mtr$wrapOpenDoors(VehicleExtraData instance, Operation<Void> originalOp) {
		Vehicle vehicle = (Vehicle) (Object) this;
		Rail rail = getCurrentRail(vehicle);

		long doorOpenDelay = 0;
		if (rail != null && RailSpeedHelper.isPlatform(rail)) {
			doorOpenDelay = RailSpeedHelper.getDoorOpenDelay(rail);
		}

		if (doorOpenDelay > 0) {
			long elapsedDwellTime = getElapsedDwellTime(vehicle);
			if (elapsedDwellTime >= doorOpenDelay) {
				originalOp.call(instance);
			}
		} else {
			originalOp.call(instance);
		}
	}

	@WrapOperation(
			method = "simulateStopped",
			at = @At(value = "INVOKE",
					target = "Lorg/mtr/core/data/Vehicle;startUp(JJ)V")
	)
	private void s1mtr$wrapStartUp(Vehicle instance, long departureIndex, long sidingDepartureTime,
								   Operation<Void> originalOp) {
		Vehicle vehicle = (Vehicle) (Object) this;
		Rail rail = getCurrentRail(vehicle);

		// 列车开始新一轮运行时清空上一轮累计的人为损耗
		VehicleDelayTracker.checkRun(vehicle, departureIndex, sidingDepartureTime);

		long doorCloseDelay = 0;
		if (rail != null && RailSpeedHelper.isPlatform(rail)) {
			doorCloseDelay = RailSpeedHelper.getDoorCloseDelay(rail);
		}

		if (doorCloseDelay <= 0) {
			DOOR_DELAY_ELAPSED_MS.remove(System.identityHashCode(vehicle));
			DOOR_HAS_BEEN_CLOSING.remove(System.identityHashCode(vehicle));
			originalOp.call(instance, departureIndex, sidingDepartureTime);
			return;
		}

		long doorCooldown = getDoorCooldown(vehicle);
		long vehicleId = System.identityHashCode(vehicle);

		// 阶段1：门正在关闭（doorCooldown > 0），允许 startUp 调用让 MTR 处理关门动画
		if (doorCooldown > 0) {
			DOOR_HAS_BEEN_CLOSING.put(vehicleId, true);
			originalOp.call(instance, departureIndex, sidingDepartureTime);
			return;
		}

		// 阶段2：首次调用 startUp（门还开着，doorCooldown==0），触发关门
		if (!DOOR_HAS_BEEN_CLOSING.getOrDefault(vehicleId, false)) {
			DOOR_HAS_BEEN_CLOSING.put(vehicleId, true);
			originalOp.call(instance, departureIndex, sidingDepartureTime);
			return;
		}

		// 阶段3：门已关闭（doorCooldown==0 且已关过门），用模拟时间累加延迟发车
		long elapsed = DOOR_DELAY_ELAPSED_MS.getOrDefault(vehicleId, 0L);
		elapsed += s1mtr$currentSimDeltaMs;
		DOOR_DELAY_ELAPSED_MS.put(vehicleId, elapsed);

		// 记录这段人为延迟：MTR 会把"实际用时超出计划用时"当成晚点并触发追点加速，
		// 这里把延迟登记下来，在计算晚点时扣除，使延迟不被计入晚点。
		VehicleDelayTracker.add(vehicle, s1mtr$currentSimDeltaMs);

		if (elapsed >= doorCloseDelay) {
			DOOR_DELAY_ELAPSED_MS.remove(vehicleId);
			DOOR_HAS_BEEN_CLOSING.remove(vehicleId);
			// 把累计的关门延迟加进 sidingDepartureTime 参数，使 MTR 最终写入的发车基准
			// 等于「真实发车时刻」，时刻表/倒数器不会认为列车提前。
			originalOp.call(instance, departureIndex, sidingDepartureTime + elapsed);
		}
		// 否则继续等待，不调用 startUp
	}

	@Inject(method = "simulateMoving", at = @At("HEAD"))
	private void s1mtr$clearDelayOnMove(CallbackInfo ci) {
		Vehicle vehicle = (Vehicle) (Object) this;
		long vehicleId = System.identityHashCode(vehicle);
		DOOR_DELAY_ELAPSED_MS.remove(vehicleId);
		DOOR_HAS_BEEN_CLOSING.remove(vehicleId);
	}

	/**
	 * 把模组人为引入的延迟(发车延迟、侧线限速)从 MTR 的「追点判定」中剔除。
	 * <p>
	 * 关键点：<b>绝不能改 {@code getTimeAlongRoute}</b>。它除了给 {@code updateDeviation}
	 * 算晚点用，还在 {@code simulate} 里被写入 {@code departureIndex -> timeAlongRoute} 映射，
	 * 那是时刻表/JCM 发车倒数器的数据源。之前给它加 delay 会让倒数器以为列车「提前」到达，
	 * 于是提前开始倒数、倒数到 0 又与真实停站脱节。
	 * <p>
	 * 正确做法：只在 {@code updateDeviation} 算出 {@code deviation} 之后，把人为延迟从
	 * {@code deviation} 字段里扣掉——列车仍按真实时间运行和上报，只是不因运营需要的延迟被判晚点、
	 * 不触发追点加速。
	 */
	@Inject(method = "updateDeviation", at = @At("TAIL"))
	private void s1mtr$excludeArtificialDelayFromDeviation(CallbackInfo ci) {
		final long artificial = VehicleDelayTracker.get(this);
		if (artificial <= 0) {
			return;
		}
		try {
			final Field field = findField(Vehicle.class, "deviation");
			if (field != null) {
				field.setAccessible(true);
				field.setLong(this, field.getLong(this) - artificial);
			}
		} catch (Exception ignored) {
		}
	}

	private Rail getCurrentRail(Vehicle vehicle) {
		try {
			Field vehicleExtraDataField = Vehicle.class.getDeclaredField("vehicleExtraData");
			vehicleExtraDataField.setAccessible(true);
			VehicleExtraData vehicleExtraData = (VehicleExtraData) vehicleExtraDataField.get(vehicle);

			Field pathField = VehicleExtraData.class.getDeclaredField("immutablePath");
			pathField.setAccessible(true);
			Object pathObj = pathField.get(vehicleExtraData);
			if (pathObj instanceof ObjectImmutableList) {
				ObjectImmutableList<?> path = (ObjectImmutableList<?>) pathObj;

				Field railProgressField = findField(Vehicle.class, "railProgress");
				if (railProgressField != null) {
					railProgressField.setAccessible(true);
					double railProgress = railProgressField.getDouble(vehicle);

					for (int i = 0; i < path.size(); i++) {
						Object obj = path.get(i);
						if (obj instanceof PathData) {
							PathData pathData = (PathData) obj;
							double startDistance = pathData.getStartDistance();
							double endDistance = pathData.getEndDistance();
							if (railProgress >= startDistance && railProgress <= endDistance) {
								return pathData.getRail();
							}
						}
					}
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private long getElapsedDwellTime(Vehicle vehicle) {
		try {
			Field field = findField(Vehicle.class, "elapsedDwellTime");
			if (field != null) {
				field.setAccessible(true);
				return field.getLong(vehicle);
			}
		} catch (Exception ignored) {
		}
		return 0;
	}

	private long getDoorCooldown(Vehicle vehicle) {
		try {
			Field field = Vehicle.class.getDeclaredField("doorCooldown");
			field.setAccessible(true);
			return field.getLong(vehicle);
		} catch (Exception ignored) {
		}
		return 0;
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
