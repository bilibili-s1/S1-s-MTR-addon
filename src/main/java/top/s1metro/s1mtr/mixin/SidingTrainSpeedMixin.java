package top.s1metro.s1mtr.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 侧线列车限速的时刻表适配（移植自 magic-mtr，MIT）。
 * <p>
 * MTR 在 {@code Siding.generatePathDistancesAndTimeSegments} 里按轨道限速计算运行时间，
 * 若只限制实际驾驶速度而不限制时刻表，设了列车限速后会永远晚点、触发追点加速。
 * 这里按 magic-mtr 的方案把时刻表计算也用 min(轨道限速, 列车限速) 封顶，
 * 并在限速改变后立即重建时间分段（保留在途列车），使运行与时刻表一致。
 */
@Mixin(value = Siding.class, remap = false)
public abstract class SidingTrainSpeedMixin {

	@Shadow(remap = false)
	public abstract double getMaxManualSpeed();

	@Shadow(remap = false)
	private ObjectArrayList<PathData> pathMainRoute;

	@Unique
	private double s1mtr$maxSpeedBeforeUpdate;

	@Redirect(
			method = "generatePathDistancesAndTimeSegments",
			at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Siding;getUpcomingSlowerSpeed(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectList;IDDD)D"),
			remap = false
	)
	private double s1mtr$capUpcomingSlowerSpeed(ObjectList<PathData> path, int startIndex,
												double railProgress, double speed, double deceleration) {
		return s1mtr$cap(Siding.getUpcomingSlowerSpeed(path, startIndex, railProgress, speed, deceleration));
	}

	@Redirect(
			method = "generatePathDistancesAndTimeSegments",
			at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getSpeedLimitMetersPerMillisecond()D"),
			remap = false
	)
	private double s1mtr$capPathSpeedLimit(PathData pathData) {
		return s1mtr$cap(pathData.getSpeedLimitMetersPerMillisecond());
	}

	private double s1mtr$cap(double speed) {
		final double max = getMaxManualSpeed();
		return Double.isFinite(max) && max > 0 && speed > max ? max : speed;
	}

	@Inject(method = "updateData", at = @At("HEAD"), remap = false)
	private void s1mtr$rememberMaxSpeed(org.mtr.core.serializer.ReaderBase readerBase, CallbackInfo ci) {
		s1mtr$maxSpeedBeforeUpdate = getMaxManualSpeed();
	}

	/** 限速变化后立即重建时刻表（否则要等车厂刷新），保留在途列车。 */
	@Inject(method = "updateData", at = @At("TAIL"), remap = false)
	private void s1mtr$regenerateOnSpeedChange(org.mtr.core.serializer.ReaderBase readerBase, CallbackInfo ci) {
		if (pathMainRoute == null || pathMainRoute.isEmpty()) {
			return;
		}
		final double before = s1mtr$maxSpeedBeforeUpdate;
		final double after = getMaxManualSpeed();
		final boolean beforeValid = Double.isFinite(before) && before > 0;
		final boolean afterValid = Double.isFinite(after) && after > 0;
		final boolean changed = beforeValid != afterValid
				|| (beforeValid && Math.abs(before - after) > Math.max(before, after) * 1e-9);
		if (!changed) {
			return;
		}
		try {
			final java.lang.reflect.Field vehiclesField = findField(Siding.class, "vehicles");
			if (vehiclesField == null) {
				return;
			}
			vehiclesField.setAccessible(true);
			final ObjectArraySet<Vehicle> vehicles = (ObjectArraySet<Vehicle>) vehiclesField.get(this);
			final ObjectArrayList<Vehicle> running = new ObjectArrayList<>(vehicles);
			final java.lang.reflect.Method regen = Siding.class.getDeclaredMethod("generatePathDistancesAndTimeSegments");
			regen.setAccessible(true);
			regen.invoke(this);
			vehicles.addAll(running);
		} catch (Exception ignored) {
		}
	}

	private static java.lang.reflect.Field findField(Class<?> startClass, String fieldName) {
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
