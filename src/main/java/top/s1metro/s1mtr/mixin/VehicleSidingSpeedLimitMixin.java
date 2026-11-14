package top.s1metro.s1mtr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Vehicle;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.s1metro.s1mtr.service.SidingSpeedLimit;
import top.s1metro.s1mtr.service.VehicleDelayTracker;

/**
 * 侧线限速的运行时生效逻辑。
 * <p>
 * MTR 在 Vehicle.simulateMoving 中用轨道自身限速计算速度目标:
 * speedTarget = pathData.getSpeedLimitMetersPerMillisecond() * deviationSpeedAdjustment
 * <p>
 * 本 Mixin 在该调用处把速度目标压到侧线限速以内,语义为 min(轨道限速, 侧线限速):
 * 侧线限速 80 时,轨道限速 60 仍按 60 行驶,轨道限速 100 按 80 行驶。
 * deviationSpeedAdjustment 是 MTR 的晚点追点系数,会等比放大速度目标,
 * 这里预先把该系数除回去,保证最终速度目标就是侧线限速本身。
 * 同时统计限速造成的时间损耗,交给 VehicleDelayTracker 在晚点判定时扣除。
 */
@Mixin(Vehicle.class)
public abstract class VehicleSidingSpeedLimitMixin {

	@Shadow
	private double deviationSpeedAdjustment;

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
		final double original = originalOp.call(instance);
		final long limit = SidingSpeedLimit.get(instance.getRail());
		if (limit <= 0) {
			return original;
		}

		final double limited = Utilities.kilometersPerHourToMetersPerMillisecond(limit);
		if (limited >= original) {
			return original;
		}

		final double delta = original - limited;
		if (delta > 0 && original > 0 && s1mtr$simDeltaMs > 0) {
			VehicleDelayTracker.add(this, Math.round(delta * s1mtr$simDeltaMs / original));
		}

		final double adjustment = this.deviationSpeedAdjustment;
		return adjustment > 1.0D ? limited / adjustment : limited;
	}
}
