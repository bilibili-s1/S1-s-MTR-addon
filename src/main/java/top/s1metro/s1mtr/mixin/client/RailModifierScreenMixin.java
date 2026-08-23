package top.s1metro.s1mtr.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screen.Screen;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Rail.Shape;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.screen.RailModifierScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.s1metro.s1mtr.client.RailSpeedHelper;
import top.s1metro.s1mtr.client.screen.RailAdvancedSettingsScreen;

@Mixin(RailModifierScreen.class)
public abstract class RailModifierScreenMixin {

	@Shadow
	@Final
	private Rail rail;

	@Shadow
	@Final
	private int buttonsWidth;

	@Shadow
	@Final
	private int xStart;

	@Shadow
	private double radius;

	@Shadow
	private void update(double radius, boolean notifyServer) {
	}

	private ButtonWidgetExtension s1mtr$advancedSettingsButton;
	private MutableText s1mtr$advancedSettingsLabel;

	@Inject(method = "init2", at = @At("TAIL"))
	private void s1mtr$initAdvancedSettingsButton(CallbackInfo ci) {
		s1mtr$advancedSettingsLabel = TextHelper.translatable("gui.s1mtr.advanced_settings");

		int screenHeight = ((Screen) (Object) this).height;
		s1mtr$advancedSettingsButton = new ButtonWidgetExtension(
				xStart + 2, screenHeight - 22, buttonsWidth - 4, 20,
				s1mtr$advancedSettingsLabel,
				button -> openAdvancedSettings());

		RailModifierScreen self = (RailModifierScreen) (Object) this;
		self.addChild(new ClickableWidget(s1mtr$advancedSettingsButton));
	}

	private void openAdvancedSettings() {
		if (rail == null) {
			return;
		}
		Shape shape = RailSpeedHelper.getShape(rail);
		RailAdvancedSettingsScreen.open(rail, radius, shape);
	}

	@Inject(method = "tick2", at = @At("TAIL"))
	private void s1mtr$tickAdvancedSettingsButton(CallbackInfo ci) {
	}

	@WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Rail;copy(Lorg/mtr/core/data/Rail;Lorg/mtr/core/data/Rail$Shape;D)Lorg/mtr/core/data/Rail;"))
	private Rail s1mtr$wrapCopyShapeRadius(
			Rail original, Rail.Shape shape, double radius,
			Operation<Rail> originalOp) {
		long speed = RailSpeedHelper.getSpeedLimit2(original);
		long doorOpenDelay = RailSpeedHelper.getDoorOpenDelay(original) / 1000;
		long doorCloseDelay = RailSpeedHelper.getDoorCloseDelay(original) / 1000;

		if (speed > 0 || doorOpenDelay > 0 || doorCloseDelay > 0) {
			return RailSpeedHelper.copyWithCustomParams(
					original, shape, radius,
					RailSpeedHelper.getStyles(original), speed, doorOpenDelay, doorCloseDelay);
		}
		return originalOp.call(original, shape, radius);
	}

	@WrapOperation(method = "lambda$new$3", at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Rail;copy(Lorg/mtr/core/data/Rail;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;)Lorg/mtr/core/data/Rail;"))
	private Rail s1mtr$wrapCopyStyles(
			Rail original, ObjectArrayList<String> styles,
			Operation<Rail> originalOp) {
		long speed = RailSpeedHelper.getSpeedLimit2(original);
		long doorOpenDelay = RailSpeedHelper.getDoorOpenDelay(original) / 1000;
		long doorCloseDelay = RailSpeedHelper.getDoorCloseDelay(original) / 1000;

		if (speed > 0 || doorOpenDelay > 0 || doorCloseDelay > 0) {
			return RailSpeedHelper.copyWithCustomParams(
					original,
					RailSpeedHelper.getShape(original),
					RailSpeedHelper.getRadius(original),
					styles, speed, doorOpenDelay, doorCloseDelay);
		}
		return originalOp.call(original, styles);
	}
}
