package top.s1metro.s1mtr.mixin;

import org.mtr.core.tool.Angle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 MTR 的 {@link Angle} 提供任意精度实例支持（万向节点的核心前提）。
 * <p>
 * {@code Angle} 是 16 向 22.5° 枚举，而万向节点的最大半径圆弧需要任意角度。本 mixin 用
 * {@code @Invoker("<init>")} 创建 {@code ordinal = -1} 的非枚举「幻影实例」，并填充预计算三角函数。
 * 枚举内部用 {@code switch} 实现的 {@code getOpposite/isParallel/add/sub/getClosest45} 对幻影实例
 * 会崩溃，因此对这些方法做 {@code @Inject} 兜底为算术计算。
 *
 * <p>移植自 FangSu For MTR 4（MIT），已在 MTR 4.0.4 / 4.0.5 字节码上核对字段与方法签名一致。
 */
@Mixin(value = Angle.class, remap = false)
public abstract class AngleMixin implements AngleExtra {

	@Shadow(remap = false)
	@Final
	public float angleDegrees;

	@Shadow(remap = false)
	@Final
	@Mutable
	public double angleRadians, sin, cos, tan, halfTan;

	@Invoker(value = "<init>")
	private static Angle create(String name, int ordinal, float angleDegrees) {
		throw new IllegalStateException();
	}

	private boolean s1mtr$isPhantom() {
		final Object self = this;
		for (final Angle a : Angle.values()) {
			if (a == self) {
				return false;
			}
		}
		return true;
	}

	private static float s1mtr$normalizeDegreesTo360(double degrees) {
		double normalized = degrees % 360.0;
		if (normalized < 0) {
			normalized += 360.0;
		}
		return (float) normalized;
	}

	/** 恰好等于某个枚举常量（22.5° 倍数）时返回该常量，保证 core 中 {@code ==} 比较在两种轨道间成立。 */
	private Angle s1mtr$snapToEnumIfExact(float degrees) {
		for (final Angle a : Angle.values()) {
			float field = a.angleDegrees;
			if (field < 0) {
				field += 360f;
			}
			if (field == degrees) {
				return a;
			}
		}
		return null;
	}

	@Override
	public Angle s1mtr$fromDegrees(double angleDegrees) {
		final float degrees = s1mtr$normalizeDegreesTo360(angleDegrees);
		final Angle exact = s1mtr$snapToEnumIfExact(degrees);
		if (exact != null) {
			return exact;
		}
		final Angle cached = AngleExtra.PHANTOM_CACHE.get(degrees);
		if (cached != null) {
			return cached;
		}
		final Angle result = create("D" + Float.toString(degrees), -1, degrees);
		s1mtr$setRadiansInternal(result, Math.toRadians(angleDegrees));
		AngleExtra.PHANTOM_CACHE.put(degrees, result);
		return result;
	}

	@Override
	public Angle s1mtr$fromRadians(double angleRadians) {
		final float degrees = s1mtr$normalizeDegreesTo360(Math.toDegrees(angleRadians));
		final Angle exact = s1mtr$snapToEnumIfExact(degrees);
		if (exact != null) {
			return exact;
		}
		final Angle cached = AngleExtra.PHANTOM_CACHE.get(degrees);
		if (cached != null) {
			return cached;
		}
		final Angle result = create("R" + Float.toString(degrees), -1, degrees);
		s1mtr$setRadiansInternal(result, angleRadians);
		AngleExtra.PHANTOM_CACHE.put(degrees, result);
		return result;
	}

	@Override
	public void s1mtr$setRadians(double angleRadians) {
		this.angleRadians = angleRadians;
		this.sin = Math.sin(angleRadians);
		this.cos = Math.cos(angleRadians);
		this.tan = Math.tan(angleRadians);
		this.halfTan = Math.tan(angleRadians / 2);
	}

	private static void s1mtr$setRadiansInternal(Angle angle, double angleRadians) {
		((AngleExtra) (Object) angle).s1mtr$setRadians(angleRadians);
	}

	private float s1mtr$normalize(float v) {
		float n = v % 360;
		if (n < 0) {
			n += 360;
		}
		return n;
	}

	private static double s1mtr$normalize(double v) {
		v = v % 360.0;
		if (v < 0) {
			v += 360.0;
		}
		return v;
	}

	@Inject(method = "getOpposite", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$getOpposite(CallbackInfoReturnable<Angle> cir) {
		if (s1mtr$isPhantom()) {
			cir.setReturnValue(AngleExtra.fromDegrees(Math.toDegrees(angleRadians) + 180));
		}
	}

	@Inject(method = "isParallel", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$isParallel(Angle angle, CallbackInfoReturnable<Boolean> cir) {
		if (s1mtr$isPhantom() || angle.ordinal() < 0) {
			final float diff = Math.abs(s1mtr$normalize(angleDegrees - angle.angleDegrees));
			cir.setReturnValue(diff < 0.001f || Math.abs(diff - 180f) < 0.001f);
		}
	}

	@Inject(method = "add", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$add(Angle angle, CallbackInfoReturnable<Angle> cir) {
		if (s1mtr$isPhantom() || angle.ordinal() < 0) {
			cir.setReturnValue(AngleExtra.fromDegrees(angleDegrees + angle.angleDegrees));
		}
	}

	@Inject(method = "sub", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$sub(Angle angle, CallbackInfoReturnable<Angle> cir) {
		if (s1mtr$isPhantom() || angle.ordinal() < 0) {
			// 差值取 [0,360) 作为 angleDegrees，radians 归一化到 [-180,180)：
			// RailMath 用 angleDifference 的符号判断几何分支，未归一化会误判退化。
			final float diff360 = s1mtr$normalizeDegreesTo360(angleDegrees - angle.angleDegrees);
			final Angle exact = s1mtr$snapToEnumIfExact(diff360);
			if (exact != null) {
				cir.setReturnValue(exact);
				return;
			}
			final float diff180 = diff360 > 180 ? diff360 - 360 : diff360;
			final Angle result = create("S" + Float.toString(diff360), -1, diff360);
			s1mtr$setRadiansInternal(result, Math.toRadians(diff180));
			cir.setReturnValue(result);
		}
	}

	@Inject(method = "getClosest45", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$getClosest45(CallbackInfoReturnable<Angle> cir) {
		if (s1mtr$isPhantom()) {
			final double snapped = Math.round(angleDegrees / 45.0) * 45.0;
			cir.setReturnValue(AngleExtra.fromDegrees(s1mtr$normalize(snapped)));
		}
	}
}
