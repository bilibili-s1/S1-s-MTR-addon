package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Rail.Shape;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.registry.RegistryClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.InitClient;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.RailSpeedHelper;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;

public class RailAdvancedSettingsScreen extends MTRScreenBase {

	private static Rail s1mtr$currentRail;
	private static double s1mtr$currentRadius;
	private static Shape s1mtr$currentShape;

	private TextFieldWidgetExtension textFieldSpeed;
	private TextFieldWidgetExtension textFieldDoorOpenDelay;
	private TextFieldWidgetExtension textFieldDoorCloseDelay;

	private int speedFieldY = 40;
	private int doorOpenDelayY = 80;
	private int doorCloseDelayY = 108;
	private int labelWidth = 80;

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

		int xStart = width / 2 - 100;
		int width = 200;

		Rail rail = getRail();

		textFieldSpeed = new TextFieldWidgetExtension(
				xStart + labelWidth + 2, speedFieldY, width - labelWidth - 6, 20, 4,
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

			boolean isSiding = ((RailSchemaAccessor) (Object) rail).s1mtr$isSiding();
			boolean canTurnBack = ((RailSchemaAccessor) (Object) rail).s1mtr$canTurnBack();
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

		boolean isPlatform = rail != null && RailSpeedHelper.isPlatform(rail);

		if (rail != null) {
			recommendedSpeed = RailSpeedHelper.calculateRecommendedSpeed(rail);
		}

		if (isPlatform) {
			textFieldDoorOpenDelay = new TextFieldWidgetExtension(
					xStart + labelWidth + 2, doorOpenDelayY, width - labelWidth - 6, 20, 4,
					org.mtr.mapping.tool.TextCase.DEFAULT,
					"[^\\d]",
					"0");
			addChild(new ClickableWidget(textFieldDoorOpenDelay));

			textFieldDoorCloseDelay = new TextFieldWidgetExtension(
					xStart + labelWidth + 2, doorCloseDelayY, width - labelWidth - 6, 20, 4,
					org.mtr.mapping.tool.TextCase.DEFAULT,
					"[^\\d]",
					"0");
			addChild(new ClickableWidget(textFieldDoorCloseDelay));

			if (rail != null) {
				long openDelay = RailSpeedHelper.getDoorOpenDelay(rail) / 1000;
				long closeDelay = RailSpeedHelper.getDoorCloseDelay(rail) / 1000;
				if (openDelay > 0) {
					textFieldDoorOpenDelay.setText2(String.valueOf(openDelay));
					pendingDoorOpenDelay = openDelay;
				}
				if (closeDelay > 0) {
					textFieldDoorCloseDelay.setText2(String.valueOf(closeDelay));
					pendingDoorCloseDelay = closeDelay;
				}
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

		MutableText doneText = TextHelper.translatable("gui.done");
		ButtonWidgetExtension doneButton = new ButtonWidgetExtension(
				xStart, height - 40, width, 20, doneText, button -> {
			saveAndClose();
		});
		addChild(new ClickableWidget(doneButton));

		MutableText cancelText = TextHelper.translatable("gui.cancel");
		ButtonWidgetExtension cancelButton = new ButtonWidgetExtension(
				xStart, height - 64, width, 20, cancelText, button -> {
			MinecraftClient.getInstance().setScreen(null);
		});
		addChild(new ClickableWidget(cancelButton));
	}

	private void saveAndClose() {
		Rail rail = getRail();
		if (rail != null) {
			Rail updatedRail = RailSpeedHelper.copyWithCustomParams(
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

		int xStart = width / 2 - 100;
		int labelX = xStart + 2;

		if (textFieldSpeed != null) {
			graphicsHolder.drawText(
					TextHelper.translatable("gui.s1mtr.speed"),
					xStart + 2, speedFieldY + 6,
					-1, false,
					GraphicsHolder.getDefaultLight());
			
			if (recommendedSpeed != 0) {
				MutableText recommendText;
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

		MutableText title = TextHelper.translatable("gui.s1mtr.advanced_settings");
		graphicsHolder.drawCenteredText(
				title,
				width / 2, 20,
				-1);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed2(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			MinecraftClient.getInstance().setScreen(null);
			return true;
		}
		return super.keyPressed2(keyCode, scanCode, modifiers);
	}
}
