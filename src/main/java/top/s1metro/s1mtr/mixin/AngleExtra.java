package top.s1metro.s1mtr.mixin;

import org.mtr.core.tool.Angle;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 任意精度角度入口。
 * <p>
 * MTR 的 {@link Angle} 是 16 个常量 / 22.5° 的枚举，万向节点需要任意角度。
 * {@link AngleMixin} 通过 {@code @Invoker("<init>")} 创建 {@code ordinal = -1} 的「幻影实例」绕过该限制，
 * 本接口提供静态入口与实例缓存（同一 float32 角度复用同一实例，保证 MTR core 里 {@code ==} 引用比较成立）。
 *
 * <p>移植自 FangSu For MTR 4（MIT），按 s1mtr 的 Yarn 映射与包名重写。
 */
public interface AngleExtra {

	/** 幻影角度缓存，键为归一化到 [0,360) 的 float32 度数。 */
	ConcurrentHashMap<Float, Angle> PHANTOM_CACHE = new ConcurrentHashMap<>();

	Angle s1mtr$fromDegrees(double degrees);

	Angle s1mtr$fromRadians(double radians);

	void s1mtr$setRadians(double radians);

	/** 生成任意精度角度；恰好是 22.5° 倍数时返回原枚举常量。 */
	static Angle fromDegrees(double degrees) {
		return ((AngleExtra) (Object) Angle.S).s1mtr$fromDegrees(degrees);
	}

	/** 生成任意精度角度（弧度）。 */
	static Angle fromRadians(double radians) {
		return ((AngleExtra) (Object) Angle.S).s1mtr$fromRadians(radians);
	}
}
