package top.s1metro.s1mtr.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.Rail;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.screen.RailStyleSelectorScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import top.s1metro.s1mtr.client.RailSpeedHelper;

@Mixin(RailStyleSelectorScreen.class)
public abstract class RailStyleSelectorScreenMixin {

	@Shadow
	@Final
	private Rail rail;

	@WrapOperation(
			method = "updateList",
			at = @At(value = "INVOKE",
					target = "Lorg/mtr/core/data/Rail;copy(Lorg/mtr/core/data/Rail;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;)Lorg/mtr/core/data/Rail;")
	)
	private Rail s1mtr$wrapCopyStyles(Rail original, ObjectArrayList<String> styles, Operation<Rail> originalOp) {
		long speedLimit1 = RailSpeedHelper.getSpeedLimit1(rail);
		long speedLimit2 = RailSpeedHelper.getSpeedLimit2(rail);

		long doorOpenDelay = RailSpeedHelper.getDoorOpenDelay(rail) / 1000;
		long doorCloseDelay = RailSpeedHelper.getDoorCloseDelay(rail) / 1000;

		if (speedLimit1 > 0 || speedLimit2 > 0 || doorOpenDelay > 0 || doorCloseDelay > 0) {
			long speed = (speedLimit2 > 0) ? speedLimit2 : speedLimit1;
			return RailSpeedHelper.copyWithCustomParams(
					original,
					RailSpeedHelper.getShape(original),
					RailSpeedHelper.getRadius(original),
					styles, speed, doorOpenDelay, doorCloseDelay);
		}

		return originalOp.call(original, styles);
	}
}