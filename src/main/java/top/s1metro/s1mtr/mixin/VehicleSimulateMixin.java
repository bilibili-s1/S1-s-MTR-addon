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
import top.s1metro.s1mtr.common.RailSettings;
import top.s1metro.s1mtr.common.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Vehicle.class)
public abstract class VehicleSimulateMixin {
    private static final ThreadLocal<Long> s1mtr$simulationDeltaMillis = ThreadLocal.withInitial(() -> 0L);
    private static final ConcurrentHashMap<Integer, Long> s1mtr$departureDelayElapsed = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Boolean> s1mtr$hasStartedClosing = new ConcurrentHashMap<>();

    @Inject(method = "simulateStopped", at = @At("HEAD"), require = 0)
    private void s1mtr$captureSimulationDelta(long delta, ObjectArrayList<?> list, int index, CallbackInfo callbackInfo) {
        s1mtr$simulationDeltaMillis.set(Math.max(0, delta));
    }

    @WrapOperation(
            method = "simulateStopped",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/VehicleExtraData;openDoors()V"),
            require = 0
    )
    private void s1mtr$delayDoorOpening(VehicleExtraData instance, Operation<Void> original) {
        final Vehicle vehicle = (Vehicle) (Object) this;
        final Rail rail = s1mtr$getCurrentRail(vehicle);
        final long delay = rail != null && RailSettings.isPlatform(rail)
                ? RailSettings.getDoorOpenDelayMillis(rail)
                : 0;
        if (delay <= 0 || s1mtr$getLongField(vehicle, "elapsedDwellTime") >= delay) {
            original.call(instance);
        }
    }

    @WrapOperation(
            method = "simulateStopped",
            at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Vehicle;startUp(JJ)V"),
            require = 0
    )
    private void s1mtr$delayDeparture(
            Vehicle instance, long departureIndex, long sidingDepartureTime, Operation<Void> original
    ) {
        final Vehicle vehicle = (Vehicle) (Object) this;
        final Rail rail = s1mtr$getCurrentRail(vehicle);
        final long delay = rail != null && RailSettings.isPlatform(rail)
                ? RailSettings.getDoorCloseDelayMillis(rail)
                : 0;
        final int key = System.identityHashCode(vehicle);

        if (delay <= 0) {
            s1mtr$clear(key);
            original.call(instance, departureIndex, sidingDepartureTime);
            return;
        }

        final long doorCooldown = s1mtr$getLongField(vehicle, "doorCooldown");
        if (doorCooldown > 0) {
            s1mtr$hasStartedClosing.put(key, true);
            original.call(instance, departureIndex, sidingDepartureTime);
            return;
        }

        if (!s1mtr$hasStartedClosing.getOrDefault(key, false)) {
            s1mtr$hasStartedClosing.put(key, true);
            original.call(instance, departureIndex, sidingDepartureTime);
            return;
        }

        final long elapsed = s1mtr$departureDelayElapsed.getOrDefault(key, 0L) + s1mtr$simulationDeltaMillis.get();
        s1mtr$departureDelayElapsed.put(key, elapsed);
        if (elapsed >= delay) {
            s1mtr$clear(key);
            original.call(instance, departureIndex, sidingDepartureTime);
        }
    }

    @Inject(method = "simulateMoving", at = @At("HEAD"), require = 0)
    private void s1mtr$clearDepartureDelayWhenMoving(CallbackInfo callbackInfo) {
        s1mtr$clear(System.identityHashCode(this));
    }

    private static void s1mtr$clear(int key) {
        s1mtr$departureDelayElapsed.remove(key);
        s1mtr$hasStartedClosing.remove(key);
    }

    private static Rail s1mtr$getCurrentRail(Vehicle vehicle) {
        try {
            final Field extraDataField = ReflectionUtil.findField(Vehicle.class, "vehicleExtraData");
            if (extraDataField == null) return null;
            final Object extraData = extraDataField.get(vehicle);
            if (!(extraData instanceof VehicleExtraData vehicleExtraData)) return null;

            final Field pathField = ReflectionUtil.findField(VehicleExtraData.class, "immutablePath");
            if (pathField == null) return null;
            final Object pathValue = pathField.get(vehicleExtraData);
            if (!(pathValue instanceof ObjectImmutableList<?> path)) return null;

            final Field railProgressField = ReflectionUtil.findField(Vehicle.class, "railProgress");
            if (railProgressField == null) return null;
            final double railProgress = railProgressField.getDouble(vehicle);

            for (Object value : path) {
                if (value instanceof PathData pathData
                        && railProgress >= pathData.getStartDistance()
                        && railProgress <= pathData.getEndDistance()) {
                    return pathData.getRail();
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static long s1mtr$getLongField(Vehicle vehicle, String fieldName) {
        try {
            final Field field = ReflectionUtil.findField(Vehicle.class, fieldName);
            return field == null ? 0 : field.getLong(vehicle);
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }
}
