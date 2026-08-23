package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Rail.Shape;
import org.mtr.core.data.Station;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.RailSpeedHelper;
import top.s1metro.s1mtr.client.builder.CompositeProfile;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;
import top.s1metro.s1mtr.network.PacketS1mtrTeleport;

/**
 * MTR 轨道高级设置界面（速度/开关门延迟 + 入口进入复合构建编辑器）。
 * <p>
 * 复合构建编辑已拆分到独立的 {@link CompositeProfileEditorScreen}，避免本界面内容过挤。
 */
public class RailAdvancedSettingsScreen extends MTRScreenBase {

	private static Rail s1mtr$currentRail;
	private static double s1mtr$currentRadius;
	private static Shape s1mtr$currentShape;

	private TextFieldWidgetExtension textFieldSpeed;
	private TextFieldWidgetExtension textFieldDoorOpenDelay;
	private TextFieldWidgetExtension textFieldDoorCloseDelay;

	private final int speedFieldY = 40;
	private final int doorOpenDelayY = 80;
	private final int doorCloseDelayY = 108;
	private final int labelWidth = 80;

	private long pendingSpeed = 0;
	private long pendingDoorOpenDelay = 0;
	private long pendingDoorCloseDelay = 0;
	private long recommendedSpeed = 0;

	public static void open(Rail rail, double radius, Shape shape) {
		s1mtr$currentRail = rail;
		s1mtr$currentRadius = radius;
		s1mtr$currentShape = shape;
		MinecraftClient.getInstance().setScreen(new RailAdvancedSettingsScreen());
	}

	private Rail getRail() {
		return s1mtr$currentRail;
	}

	private double getRadius() {
		return s1mtr$currentRadius;
	}

	private Shape getShape() {
		return s1mtr$currentShape;
	}

	@Override
	protected void init2() {
		super.init2();

		final int xStart = width / 2 - 100;
		final int panelWidth = 200;

		final Rail rail = getRail();

		textFieldSpeed = new TextFieldWidgetExtension(
				xStart + labelWidth + 2, speedFieldY, panelWidth - labelWidth - 6, 20, 4,
				org.mtr.mapping.tool.TextCase.DEFAULT,
				"[^\\d]",
				"0");
		addChild(new ClickableWidget(textFieldSpeed));

		if (rail != null) {
			long currentSpeed = RailSpeedHelper.getSpeedLimit2(rail);
			if (currentSpeed <= 0) {
				currentSpeed = RailSpeedHelper.getSpeedLimit1(rail);
			}
			if (currentSpeed > 0) {
				textFieldSpeed.setText2(String.valueOf(currentSpeed));
				pendingSpeed = currentSpeed;
			}

			final boolean isSiding = ((RailSchemaAccessor) (Object) rail).s1mtr$isSiding();
			final boolean canTurnBack = ((RailSchemaAccessor) (Object) rail).s1mtr$canTurnBack();
			if (isSiding || canTurnBack) {
				textFieldSpeed.setEditable(false);
			}
		}

		textFieldSpeed.setChangedListener2(text -> {
			try {
				long s = Long.parseLong(text);
				if (s > 1000) {
					textFieldSpeed.setText2("1000");
					s = 1000;
				}
				pendingSpeed = (s >= 1 && s <= 1000) ? s : 0;
			} catch (NumberFormatException ignored) {
				pendingSpeed = 0;
			}
		});

		if (rail != null) {
			recommendedSpeed = RailSpeedHelper.calculateRecommendedSpeed(rail);
		}

		final boolean isPlatform = rail != null && RailSpeedHelper.isPlatform(rail);

		if (isPlatform) {
			textFieldDoorOpenDelay = new TextFieldWidgetExtension(
					xStart + labelWidth + 2, doorOpenDelayY, panelWidth - labelWidth - 6, 20, 4,
					org.mtr.mapping.tool.TextCase.DEFAULT,
					"[^\\d]",
					"0");
			addChild(new ClickableWidget(textFieldDoorOpenDelay));

			textFieldDoorCloseDelay = new TextFieldWidgetExtension(
					xStart + labelWidth + 2, doorCloseDelayY, panelWidth - labelWidth - 6, 20, 4,
					org.mtr.mapping.tool.TextCase.DEFAULT,
					"[^\\d]",
					"0");
			addChild(new ClickableWidget(textFieldDoorCloseDelay));

			final long openDelay = RailSpeedHelper.getDoorOpenDelay(rail) / 1000;
			final long closeDelay = RailSpeedHelper.getDoorCloseDelay(rail) / 1000;
			if (openDelay > 0) {
				textFieldDoorOpenDelay.setText2(String.valueOf(openDelay));
				pendingDoorOpenDelay = openDelay;
			}
			if (closeDelay > 0) {
				textFieldDoorCloseDelay.setText2(String.valueOf(closeDelay));
				pendingDoorCloseDelay = closeDelay;
			}

			textFieldDoorOpenDelay.setChangedListener2(text -> {
				try {
					long d = Long.parseLong(text);
					pendingDoorOpenDelay = (d >= 0 && d <= 60) ? d : 0;
				} catch (NumberFormatException ignored) {
					pendingDoorOpenDelay = 0;
				}
			});

			textFieldDoorCloseDelay.setChangedListener2(text -> {
				try {
					long d = Long.parseLong(text);
					pendingDoorCloseDelay = (d >= 0 && d <= 60) ? d : 0;
				} catch (NumberFormatException ignored) {
					pendingDoorCloseDelay = 0;
				}
			});
		}

		// 进入复合构建编辑器
		final int editorButtonY = isPlatform ? 150 : 140;
		final ButtonWidgetExtension editorButton = new ButtonWidgetExtension(
				xStart, editorButtonY, panelWidth, 20,
				TextHelper.translatable("gui.s1mtr.edit_profile"), button -> {
			final top.s1metro.s1mtr.client.builder.CompositeLayerSchedule schedule =
					rail == null ? new top.s1metro.s1mtr.client.builder.CompositeLayerSchedule()
							: RailSpeedHelper.getCompositeSchedule(rail);
			CompositeProfileEditorScreen.open(rail, schedule);
		});
		addChild(new ClickableWidget(editorButton));

		// 传送到轨道另一端:检测另一端是否在站点区域内,显示对应站点名
		final int teleportButtonY = editorButtonY + 24;
		if (rail != null) {
			final RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;
			final Position pos1 = accessor.s1mtr$getPosition1();
			final Position pos2 = accessor.s1mtr$getPosition2();
			// 玩家当前位置:用 pos1 距离判断哪一端是"另一端"
			final net.minecraft.client.network.ClientPlayerEntity player = MinecraftClient.getInstance().player;
			final net.minecraft.util.math.BlockPos playerNativePos = player == null ? null : player.getBlockPos();
			final boolean nearPos1 = playerNativePos != null && isCloserTo(playerNativePos, pos1, pos2);
			final Position otherEnd = nearPos1 ? pos2 : pos1;
			final BlockPos otherEndBlockPos = new BlockPos(
					Math.toIntExact(otherEnd.getX()),
					Math.toIntExact(otherEnd.getY()),
					Math.toIntExact(otherEnd.getZ()));
			final Station stationAtOtherEnd = InitClient.findStation(otherEndBlockPos);
			final MutableText teleportLabel;
			if (stationAtOtherEnd != null) {
				teleportLabel = TextHelper.translatable("gui.s1mtr.teleport_to_end_station", stationAtOtherEnd.getName());
			} else {
				teleportLabel = TextHelper.translatable("gui.s1mtr.teleport_to_end");
			}
			final ButtonWidgetExtension teleportButton = new ButtonWidgetExtension(
					xStart, teleportButtonY, panelWidth, 20,
					teleportLabel, button -> {
				MinecraftClient.getInstance().setScreen(null);
				top.s1metro.s1mtr.client.S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
						new PacketS1mtrTeleport(
								otherEnd.getX(),
								otherEnd.getY(),
								otherEnd.getZ()));
			});
			addChild(new ClickableWidget(teleportButton));
		}

		final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(
				xStart, height - 40, panelWidth, 20,
				TextHelper.translatable("gui.done"), button -> saveAndClose());
		addChild(new ClickableWidget(doneButton));

		final ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(
				xStart, height - 64, panelWidth, 20,
				TextHelper.translatable("gui.cancel"), button -> MinecraftClient.getInstance().setScreen(null));
		addChild(new ClickableWidget(cancelButton));
	}

	/** 判断玩家更靠近 pos1 还是 pos2(水平距离平方)。 */
	private static boolean isCloserTo(net.minecraft.util.math.BlockPos playerPos, Position pos1, Position pos2) {
		final long dx1 = playerPos.getX() - pos1.getX();
		final long dz1 = playerPos.getZ() - pos1.getZ();
		final long dx2 = playerPos.getX() - pos2.getX();
		final long dz2 = playerPos.getZ() - pos2.getZ();
		return dx1 * dx1 + dz1 * dz1 <= dx2 * dx2 + dz2 * dz2;
	}

	private void saveAndClose() {
		final Rail rail = getRail();
		if (rail != null) {
			final Rail updatedRail = RailSpeedHelper.copyWithCustomParams(
					rail, getShape(), getRadius(),
					RailSpeedHelper.getStyles(rail),
					pendingSpeed, pendingDoorOpenDelay, pendingDoorCloseDelay);
			if (updatedRail != null) {
				InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(
						new UpdateDataRequest(MinecraftClientData.getInstance())
								.addRail(updatedRail)
				));
			}
		}
		MinecraftClient.getInstance().setScreen(null);
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		final int xStart = width / 2 - 100;

		if (textFieldSpeed != null) {
			graphicsHolder.drawText(
					TextHelper.translatable("gui.s1mtr.speed"),
					xStart + 2, speedFieldY + 6,
					-1, false,
					GraphicsHolder.getDefaultLight());

			if (recommendedSpeed != 0) {
				final MutableText recommendText;
				if (recommendedSpeed == -1) {
					recommendText = TextHelper.translatable("gui.s1mtr.recommended_speed_infinite");
				} else {
					recommendText = TextHelper.translatable("gui.s1mtr.recommended_speed", recommendedSpeed);
				}
				graphicsHolder.drawText(
						recommendText,
						xStart + 2, speedFieldY + 28,
						0x7FFFFFFF, false,
						GraphicsHolder.getDefaultLight());
			}
		}

		if (textFieldDoorOpenDelay != null) {
			graphicsHolder.drawText(
					TextHelper.translatable("gui.s1mtr.door_open_delay"),
					xStart + 2, doorOpenDelayY + 6,
					-1, false,
					GraphicsHolder.getDefaultLight());
		}

		if (textFieldDoorCloseDelay != null) {
			graphicsHolder.drawText(
					TextHelper.translatable("gui.s1mtr.door_close_delay"),
					xStart + 2, doorCloseDelayY + 6,
					-1, false,
					GraphicsHolder.getDefaultLight());
		}

		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.advanced_settings"),
				width / 2, 20,
				-1);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}
}
