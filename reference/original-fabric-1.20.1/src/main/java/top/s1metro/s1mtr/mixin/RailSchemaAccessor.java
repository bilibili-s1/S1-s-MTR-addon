package top.s1metro.s1mtr.mixin;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.generated.data.RailSchema;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link RailSchema} 的 protected final 字段。
 * <p>
 * 由于 {@link Rail} 继承自 {@link RailSchema}，运行时
 * {@code (RailSchemaAccessor)(Object) rail} 强转成立，可用于读取轨道的所有持久化字段。
 * 所有方法名加 {@code s1mtr$} 前缀以避免与其他模组的 Mixin 冲突。
 */
@Mixin(RailSchema.class)
public interface RailSchemaAccessor {

	@Accessor("position1")
	Position s1mtr$getPosition1();

	@Accessor("angle1")
	Angle s1mtr$getAngle1();

	@Accessor("position2")
	Position s1mtr$getPosition2();

	@Accessor("angle2")
	Angle s1mtr$getAngle2();

	@Accessor("shape")
	Rail.Shape s1mtr$getShape();

	@Accessor("verticalRadius")
	double s1mtr$getVerticalRadius();

	@Accessor("styles")
	ObjectArrayList<String> s1mtr$getStyles();

	@Accessor("speedLimit1")
	long s1mtr$getSpeedLimit1();

	@Accessor("speedLimit2")
	long s1mtr$getSpeedLimit2();

	@Accessor("isPlatform")
	boolean s1mtr$isPlatform();

	@Accessor("isSiding")
	boolean s1mtr$isSiding();

	@Accessor("canAccelerate")
	boolean s1mtr$canAccelerate();

	@Accessor("canTurnBack")
	boolean s1mtr$canTurnBack();

	@Accessor("canConnectRemotely")
	boolean s1mtr$canConnectRemotely();

	@Accessor("canHaveSignal")
	boolean s1mtr$canHaveSignal();

	@Accessor("transportMode")
	TransportMode s1mtr$getTransportMode();
}
