package top.s1metro.s1mtr.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.tool.Angle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 放宽 MTR 侧线寻路在节点处的角度匹配，使万向节点的小角度转弯布局可以寻路。
 * <p>
 * 原版 {@code SidingPathFinder.getConnections} 要求 {@code node.angle == rail.getStartAngle(...)},
 * 是 {@code if_acmpeq} 引用比较。幻影 {@link Angle} 实例不是枚举单例，任意角度轨道会让寻路判不可达。
 * 三个注入点配合把差值 ≤ 22.5° 的比较两侧换成同一实例；ThreadLocal 避免异步寻路互相污染。
 *
 * <p>移植自 FangSu For MTR 4（MIT）；已在 MTR 4.0.4 / 4.0.5 上核对 lambda 字节码完全一致。
 * 升级 MTR 时需重新 javap 复核 {@code lambda$getConnections$0} 与 ordinal。
 */
@Mixin(value = SidingPathFinder.class, remap = false)
public abstract class SidingPathFinderMixin {

	private static final float MAX_TURN_DEVIATION = 22.5f;
	private static final ThreadLocal<Angle> S1MTR_CACHED_RAIL_ANGLE = new ThreadLocal<>();

	private static boolean s1mtr$anglesMatch(Angle a, Angle b) {
		if (a == b) {
			return true;
		}
		final float diff = Math.abs(a.angleDegrees - b.angleDegrees) % 360f;
		return Math.min(diff, 360f - diff) <= MAX_TURN_DEVIATION;
	}

	@WrapOperation(
			method = "lambda$getConnections$0",
			at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Rail;getSpeedLimitMetersPerMillisecond(Lorg/mtr/core/data/Position;)D")
	)
	private static double s1mtr$precomputeRailAngle(Rail rail, Position position, Operation<Double> original) {
		final double speedLimit = original.call(rail, position);
		S1MTR_CACHED_RAIL_ANGLE.set(speedLimit > 0 ? rail.getStartAngle(position) : null);
		return speedLimit;
	}

	@ModifyExpressionValue(
			method = "lambda$getConnections$0",
			at = @At(value = "INVOKE", ordinal = 3)
	)
	private static Angle s1mtr$matchNodeAngle(Angle nodeAngle) {
		final Angle railAngle = S1MTR_CACHED_RAIL_ANGLE.get();
		if (railAngle != null && nodeAngle != null && s1mtr$anglesMatch(nodeAngle, railAngle)) {
			return railAngle;
		}
		return nodeAngle;
	}

	@ModifyExpressionValue(
			method = "lambda$getConnections$0",
			at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Rail;getStartAngle(Lorg/mtr/core/data/Position;)Lorg/mtr/core/tool/Angle;", ordinal = 0)
	)
	private static Angle s1mtr$reuseCachedRailAngle(Angle original) {
		final Angle cached = S1MTR_CACHED_RAIL_ANGLE.get();
		return cached != null ? cached : original;
	}
}
