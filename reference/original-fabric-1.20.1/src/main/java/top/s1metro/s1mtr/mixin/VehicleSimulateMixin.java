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

		if (elapsed >= doorCloseDelay) {
			DOOR_DELAY_ELAPSED_MS.remove(vehicleId);
			DOOR_HAS_BEEN_CLOSING.remove(vehicleId);
			originalOp.call(instance, departureIndex, sidingDepartureTime);
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
