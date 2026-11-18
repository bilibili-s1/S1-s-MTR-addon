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
import top.s1metro.s1mtr.service.SidingSpeedLimit;

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
	private TextFieldWidgetExtension s1mtr$textFieldSidingSpeedLimit;
	private MutableText s1mtr$sidingSpeedLimitLabel;
	/** 侧线限速框上一次是否聚焦，用于「焦点离开时保存一次」避免边输入边重建轨道。 */
	private boolean s1mtr$sidingFieldWasFocused;

	@Inject(method = "init2", at = @At("TAIL"))
	private void s1mtr$initDelayFields(CallbackInfo ci) {
		Rail rail = s1mtr$getRailFromSavedRailBase();
		if (rail == null) {
			return;
		}

		SavedRailScreenBase<?, ?> self = (SavedRailScreenBase<?, ?>) (Object) this;
		int screenWidth = self.width;
		int screenHeight = self.height;

		int x = 20 + textWidth + 2;
		int fieldWidth = screenWidth - textWidth - 40 - 4;

		if (RailSpeedHelper.isPlatform(rail)) {
			s1mtr$initDoorDelayFields(self, rail, x, screenHeight, fieldWidth);
		}

		if (s1mtr$isSidingRail(rail)) {
			s1mtr$initSidingSpeedLimitField(self, rail, x, screenHeight, fieldWidth);
		}
	}

	private void s1mtr$initDoorDelayFields(SavedRailScreenBase<?, ?> self, Rail rail, int x, int screenHeight, int fieldWidth) {
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

		int y = screenHeight - 100;
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

	private void s1mtr$initSidingSpeedLimitField(SavedRailScreenBase<?, ?> self, Rail rail, int x, int screenHeight, int fieldWidth) {
		s1mtr$sidingSpeedLimitLabel = TextHelper.translatable("gui.s1mtr.siding_speed_limit");

		s1mtr$textFieldSidingSpeedLimit = new TextFieldWidgetExtension(
				0, 0, 0, 20, 6,
				org.mtr.mapping.tool.TextCase.DEFAULT,
				"[^0-9.]",
				"");

		// 固定放在屏幕最下方，避免与 MTR 本体的速度/停站设置控件重叠
		int y = screenHeight - 24;
		IDrawing.setPositionAndWidth(s1mtr$textFieldSidingSpeedLimit, x, y, fieldWidth);

		self.addChild(new ClickableWidget(s1mtr$textFieldSidingSpeedLimit));

		// 初始值读本模组独立存储（而不是 MTR 的 maxManualSpeed），避免与手动限速滑块同步/只读
		if (savedRailBase instanceof org.mtr.core.data.Siding siding) {
			final double kmh = top.s1metro.s1mtr.service.SidingSpeedStore.get(siding.getId());
			s1mtr$textFieldSidingSpeedLimit.setText2(String.valueOf((long) kmh));
		}
		s1mtr$sidingFieldWasFocused = false;

		// 不在输入过程中保存：边输入边重建轨道会导致文本被服务端回同步覆盖、无法输入
		s1mtr$textFieldSidingSpeedLimit.setChangedListener2(text -> {
		});
	}

	@Inject(method = "tick2", at = @At("TAIL"))
	private void s1mtr$tickDelayFields(CallbackInfo ci) {
		if (s1mtr$textFieldDoorOpenDelay != null) {
			s1mtr$textFieldDoorOpenDelay.tick2();
		}
		if (s1mtr$textFieldDoorCloseDelay != null) {
			s1mtr$textFieldDoorCloseDelay.tick2();
		}
		if (s1mtr$textFieldSidingSpeedLimit != null) {
			s1mtr$textFieldSidingSpeedLimit.tick2();
			// 焦点离开时保存一次（而不是每次按键都发包重建轨道）
			final boolean focused = s1mtr$textFieldSidingSpeedLimit.isFocused2();
			if (s1mtr$sidingFieldWasFocused && !focused) {
				s1mtr$saveSidingSpeedLimit();
			}
			s1mtr$sidingFieldWasFocused = focused;
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
		if (s1mtr$sidingSpeedLimitLabel != null && s1mtr$textFieldSidingSpeedLimit != null) {
			graphicsHolder.drawText(
					s1mtr$sidingSpeedLimitLabel,
					20, s1mtr$textFieldSidingSpeedLimit.getY() + 6,
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

			// 先查仪表板全量数据（远离车厂时世界实例里没有该轨道），再退回世界实例
			for (Data data : new Data[]{MinecraftClientData.getDashboardInstance(), MinecraftClientData.getInstance()}) {
				if (data == null) {
					continue;
				}
				Field mapField = findField(data.getClass(), "positionsToRail");
				if (mapField == null) {
					continue;
				}
				mapField.setAccessible(true);
				Object map = mapField.get(data);
				final Rail found = Data.tryGet((Map<Position, Map<Position, Rail>>) map, pos1, pos2);
				if (found != null) {
					return found;
				}
			}
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

		s1mtr$sendRailUpdate(newRail);
	}

	/**
	 * 侧线「列车最高限速」：
	 * <ol>
	 *   <li>写入本模组的独立存储 {@link top.s1metro.s1mtr.service.SidingSpeedStore}（UI 显示源，不与 MTR 手动限速同步）；</li>
	 *   <li>同步到 {@code Siding.maxManualSpeed} 作为执行层——实测这是列车限速真正生效的字段，
	 *       由 {@code VehicleSidingSpeedLimitMixin} 让自动驾驶也遵守，取 min(轨道限速, 本限速)。</li>
	 * </ol>
	 * 用 dashboard 数据实例发包，保证车厂范围外也能同步。
	 */
	private void s1mtr$saveSidingSpeedLimit() {
		if (!(savedRailBase instanceof org.mtr.core.data.Siding siding)) {
			return;
		}
		double kmh;
		try {
			kmh = Double.parseDouble(s1mtr$textFieldSidingSpeedLimit.getText2().trim());
		} catch (NumberFormatException ignored) {
			return;
		}
		if (kmh < 0 || kmh > 1000) {
			return;
		}
		// 独立 UI 存储（可编辑，与 MTR 手动限速滑块解耦）
		top.s1metro.s1mtr.service.SidingSpeedStore.set(siding.getId(), kmh);
		// 同步到服务端权威存储（不碰 maxManualSpeed，避免跟着手动驾驶限速变）
		top.s1metro.s1mtr.client.S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
				new top.s1metro.s1mtr.network.PacketS1mtrSetSidingAutoSpeed(siding.getId(), kmh));
	}

	private void s1mtr$sendRailUpdate(Rail newRail) {
		try {
			UpdateDataRequest request = new UpdateDataRequest(MinecraftClientData.getInstance());
			request.addRail(newRail);
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));
		} catch (Exception ignored) {
		}
	}

	/** 轨道是否为侧线轨道。 */
	private boolean s1mtr$isSidingRail(Rail rail) {
		try {
			return ((top.s1metro.s1mtr.mixin.RailSchemaAccessor) (Object) rail).s1mtr$isSiding();
		} catch (Exception ignored) {
			return false;
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