package top.s1metro.s1mtr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.s1metro.s1mtr.service.SidingSpeedLimit;
import top.s1metro.s1mtr.service.VehicleDelayTracker;

/**
 * 列车最高限速 / 侧线限速的运行时生效逻辑。
 * <p>
 * MTR 在 {@code Vehicle.simulateMoving} 中按轨道限速计算速度目标。本 mixin 在两个调用点封顶：
 * <ul>
 *   <li>{@code PathData.getSpeedLimitMetersPerMillisecond} — 当前轨道限速</li>
 *   <li>{@code Siding.getUpcomingSlowerSpeed} — 前方较慢区段限速（决定制动）</li>
 * </ul>
 * 两处都取 min(原限速, 列车最高限速 {@code Siding.maxManualSpeed}, styles 侧线限速)。
 * <p>
 * 关键：MTR 原版只在手动驾驶读 {@code maxManualSpeed}，这里让自动驾驶同样遵守（magic-mtr 方案）。
 * 因此用户设置的「列车最高限速」无论人工/自动驾驶都生效，且轨道限速更低时仍按轨道限速行驶。
 * deviationSpeedAdjustment 是晚点追点系数，会等比放大速度目标，预先除回去保证不超速。
 */
@Mixin(Vehicle.class)
public abstract class VehicleSidingSpeedLimitMixin {

	@Shadow
	private double deviationSpeedAdjustment;

	@Shadow(remap = false)
	public VehicleExtraData vehicleExtraData;

	private static long s1mtr$simDeltaMs = 0L;

	@Inject(method = "simulateMoving", at = @At("HEAD"))
	private void s1mtr$captureSimDelta(long millisElapsed, ObjectArrayList<?> vehiclePositions, int currentIndex, CallbackInfo ci) {
		s1mtr$simDeltaMs = millisElapsed;
	}

	@WrapOperation(
			method = "simulateMoving",
			at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getSpeedLimitMetersPerMillisecond()D")
	)
	private double s1mtr$applySidingSpeedLimit(PathData instance, Operation<Double> originalOp) {
		return s1mtr$cap(originalOp.call(instance), instance.getRail());
	}

	@WrapOperation(
			method = "simulateMoving",
			at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Siding;getUpcomingSlowerSpeed(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectList;IDDD)D")
	)
	private double s1mtr$applyUpcomingSpeedLimit(ObjectList<PathData> path, int startIndex,
												 double railProgress, double speed, double deceleration,
												 Operation<Double> originalOp) {
		final double result = originalOp.call(path, startIndex, railProgress, speed, deceleration);
		// getUpcomingSlowerSpeed 的 -1 哨兵值表示「前方无限速」，原样透传
		if (result < 0) {
			return result;
		}
		// 取路径中当前段的轨道作为 styles 限速来源（前方限速不受 styles 影响，仅用列车最高限速）
		final org.mtr.core.data.Rail rail = startIndex >= 0 && startIndex < path.size() ? path.get(startIndex).getRail() : null;
		return s1mtr$cap(result, rail);
	}

	private double s1mtr$cap(double original, org.mtr.core.data.Rail rail) {
		if (original <= 0) {
			return original;
		}
		double limited = original;

		// 来源1：列车最高限速（Siding.maxManualSpeed，MTR 原生只在手动读，这里让自动也生效）
		final double maxManual = vehicleExtraData.getMaxManualSpeed();
		if (Double.isFinite(maxManual) && maxManual > 0 && maxManual < limited) {
			limited = maxManual;
		}

		// 来源2：styles 侧线限速（可选，兼容旧数据）
		if (rail != null) {
			final long styleLimit = SidingSpeedLimit.get(rail);
			if (styleLimit > 0) {
				final double styleSpeed = Utilities.kilometersPerHourToMetersPerMillisecond(styleLimit);
				if (styleSpeed < limited) {
					limited = styleSpeed;
				}
			}
		}

		if (limited >= original) {
			return original;
		}
		final double delta = original - limited;
		if (delta > 0 && s1mtr$simDeltaMs > 0) {
			VehicleDelayTracker.add(this, Math.round(delta * s1mtr$simDeltaMs / original));
		}
		final double adjustment = this.deviationSpeedAdjustment;
		return adjustment > 1.0D ? limited / adjustment : limited;
	}
}
