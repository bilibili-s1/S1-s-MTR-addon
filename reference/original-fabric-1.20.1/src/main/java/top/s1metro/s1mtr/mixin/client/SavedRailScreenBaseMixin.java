package top.s1metro.s1mtr.mixin.client;

import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.SavedRailBase;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.SavedRailScreenBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.s1metro.s1mtr.client.RailSpeedHelper;

import java.lang.reflect.Field;
import java.util.Map;

@Mixin(SavedRailScreenBase.class)
public abstract class SavedRailScreenBaseMixin {

	@Shadow
	@Final
	protected SavedRailBase<?, ?> savedRailBase;

	@Shadow
	protected int textWidth;

	private TextFieldWidgetExtension s1mtr$textFieldDoorOpenDelay;
	private TextFieldWidgetExtension s1mtr$textFieldDoorCloseDelay;
	private MutableText s1mtr$doorOpenDelayLabel;
	private MutableText s1mtr$doorCloseDelayLabel;

	@Inject(method = "init2", at = @At("TAIL"))
	private void s1mtr$initDelayFields(CallbackInfo ci) {
		Rail rail = s1mtr$getRailFromSavedRailBase();
		if (rail == null || !RailSpeedHelper.isPlatform(rail)) {
			return;
		}

		s1mtr$doorOpenDelayLabel = TextHelper.translatable("gui.s1mtr.door_open_delay");
		s1mtr$doorCloseDelayLabel = TextHelper.translatable("gui.s1mtr.door_close_delay");

		s1mtr$textFieldDoorOpenDelay = new TextFieldWidgetExtension(
				0, 0, 0, 20, 4,
				org.mtr.mapping.tool.TextCase.DEFAULT,
				"[^\\d]",
				"0");
		s1mtr$textFieldDoorCloseDelay = new TextFieldWidgetExtension(
				0, 0, 0, 20, 4,
				org.mtr.mapping.tool.TextCase.DEFAULT,
				"[^\\d]",
				"0");

		SavedRailScreenBase<?, ?> self = (SavedRailScreenBase<?, ?>) (Object) this;
		int screenWidth = self.width;
		int screenHeight = self.height;

		int x = 20 + textWidth + 2;
		int y = screenHeight - 100;
		int fieldWidth = screenWidth - textWidth - 40 - 4;

		IDrawing.setPositionAndWidth(s1mtr$textFieldDoorOpenDelay, x, y, fieldWidth);
		IDrawing.setPositionAndWidth(s1mtr$textFieldDoorCloseDelay, x, y + 24, fieldWidth);

		self.addChild(new ClickableWidget(s1mtr$textFieldDoorOpenDelay));
		self.addChild(new ClickableWidget(s1mtr$textFieldDoorCloseDelay));

		long openDelay = RailSpeedHelper.getDoorOpenDelay(rail) / 1000;
		long closeDelay = RailSpeedHelper.getDoorCloseDelay(rail) / 1000;
		if (openDelay > 0) {
			s1mtr$textFieldDoorOpenDelay.setText2(String.valueOf(openDelay));
		}
		if (closeDelay > 0) {
			s1mtr$textFieldDoorCloseDelay.setText2(String.valueOf(closeDelay));
		}

		s1mtr$textFieldDoorOpenDelay.setChangedListener2(text -> s1mtr$saveDelayValues());
		s1mtr$textFieldDoorCloseDelay.setChangedListener2(text -> s1mtr$saveDelayValues());
	}

	@Inject(method = "tick2", at = @At("TAIL"))
	private void s1mtr$tickDelayFields(CallbackInfo ci) {
		if (s1mtr$textFieldDoorOpenDelay != null) {
			s1mtr$textFieldDoorOpenDelay.tick2();
		}
		if (s1mtr$textFieldDoorCloseDelay != null) {
			s1mtr$textFieldDoorCloseDelay.tick2();
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void s1mtr$renderDelayLabels(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (s1mtr$doorOpenDelayLabel != null && s1mtr$textFieldDoorOpenDelay != null) {
			graphicsHolder.drawText(
					s1mtr$doorOpenDelayLabel,
					20, s1mtr$textFieldDoorOpenDelay.getY() + 6,
					-1, false,
					GraphicsHolder.getDefaultLight());
		}
		if (s1mtr$doorCloseDelayLabel != null && s1mtr$textFieldDoorCloseDelay != null) {
			graphicsHolder.drawText(
					s1mtr$doorCloseDelayLabel,
					20, s1mtr$textFieldDoorCloseDelay.getY() + 6,
					-1, false,
					GraphicsHolder.getDefaultLight());
		}
	}

	private Rail s1mtr$getRailFromSavedRailBase() {
		try {
			Position pos1 = s1mtr$getPosition(savedRailBase, "position1");
			Position pos2 = s1mtr$getPosition(savedRailBase, "position2");
			if (pos1 == null || pos2 == null) {
				return null;
			}

			Data data = MinecraftClientData.getInstance();
			Field mapField = findField(data.getClass(), "positionsToRail");
			if (mapField == null) {
				return null;
			}
			mapField.setAccessible(true);
			Object map = mapField.get(data);

			return Data.tryGet((Map<Position, Map<Position, Rail>>) map, pos1, pos2);
		} catch (Exception ignored) {
		}
		return null;
	}

	private static Position s1mtr$getPosition(Object obj, String fieldName) {
		Field field = findField(obj.getClass(), fieldName);
		if (field == null) {
			return null;
		}
		try {
			field.setAccessible(true);
			return (Position) field.get(obj);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Field findField(Class<?> startClass, String fieldName) {
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

	private void s1mtr$saveDelayValues() {
		Rail rail = s1mtr$getRailFromSavedRailBase();
		if (rail == null || !RailSpeedHelper.isPlatform(rail)) {
			return;
		}

		long openDelay = s1mtr$parseDelay(s1mtr$textFieldDoorOpenDelay);
		long closeDelay = s1mtr$parseDelay(s1mtr$textFieldDoorCloseDelay);

		Rail newRail = RailSpeedHelper.copyWithCustomParams(
				rail,
				RailSpeedHelper.getShape(rail),
				RailSpeedHelper.getRadius(rail),
				RailSpeedHelper.getStyles(rail),
				RailSpeedHelper.getSpeedLimit2(rail),
				openDelay,
				closeDelay);

		try {
			UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getInstance());
			request.addRail(newRail);
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
		} catch (Exception ignored) {
		}
	}

	private long s1mtr$parseDelay(TextFieldWidgetExtension field) {
		if (field == null) return 0;
		try {
			long d = Long.parseLong(field.getText2());
			return (d >= 0 && d <= 60) ? d : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}