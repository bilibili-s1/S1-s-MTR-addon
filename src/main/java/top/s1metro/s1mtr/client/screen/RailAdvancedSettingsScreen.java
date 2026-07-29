package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.mtr.core.data.Rail;
import top.s1metro.s1mtr.S1MtrAddon;
import top.s1metro.s1mtr.client.MtrNetworkBridge;
import top.s1metro.s1mtr.common.RailSettings;

public final class RailAdvancedSettingsScreen extends Screen {
    private final Screen parent;
    private final Rail rail;
    private EditBox speedField;
    private EditBox doorOpenDelayField;
    private EditBox doorCloseDelayField;
    private long pendingSpeed;
    private long pendingDoorOpenDelay;
    private long pendingDoorCloseDelay;
    private Component statusMessage;

    public RailAdvancedSettingsScreen(Screen parent, Rail rail) {
        super(Component.translatable("gui.s1mtr.advanced_settings"));
        this.parent = parent;
        this.rail = rail;
    }

    @Override
    protected void init() {
        final int panelWidth = Math.min(300, width - 30);
        final int x = (width - panelWidth) / 2;
        final int fieldX = x + 145;
        final int fieldWidth = panelWidth - 149;
        int y = 58;

        pendingSpeed = RailSettings.getPreferredSpeedLimit(rail);
        speedField = createNumericField(fieldX, y, fieldWidth, 20, 4, pendingSpeed, 1, 1000, value -> pendingSpeed = value);
        speedField.setEditable(!RailSettings.isSiding(rail) && !RailSettings.canTurnBack(rail));
        addRenderableWidget(speedField);
        y += 38;

        if (RailSettings.isPlatform(rail)) {
            pendingDoorOpenDelay = RailSettings.getDoorOpenDelayMillis(rail) / 1000;
            pendingDoorCloseDelay = RailSettings.getDoorCloseDelayMillis(rail) / 1000;
            doorOpenDelayField = createNumericField(fieldX, y, fieldWidth, 20, 2, pendingDoorOpenDelay, 0, 60, value -> pendingDoorOpenDelay = value);
            addRenderableWidget(doorOpenDelayField);
            y += 38;
            doorCloseDelayField = createNumericField(fieldX, y, fieldWidth, 20, 2, pendingDoorCloseDelay, 0, 60, value -> pendingDoorCloseDelay = value);
            addRenderableWidget(doorCloseDelayField);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(x, height - 48, (panelWidth - 6) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(x + (panelWidth + 6) / 2, height - 48, (panelWidth - 6) / 2, 20).build());
    }

    private EditBox createNumericField(int x, int y, int width, int height, int maxLength, long initial,
                                       long minimum, long maximum, java.util.function.LongConsumer responder) {
        final EditBox editBox = new EditBox(font, x, y, width, height, Component.empty());
        editBox.setMaxLength(maxLength);
        editBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
        editBox.setValue(Long.toString(initial));
        editBox.setResponder(value -> responder.accept(parseAndClamp(value, minimum, maximum)));
        return editBox;
    }

    private static long parseAndClamp(String text, long minimum, long maximum) {
        try {
            final long value = Long.parseLong(text);
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException ignored) {
            return minimum;
        }
    }

    private void saveAndClose() {
        // Read the widgets at click time. Relying only on responders can retain a
        // stale value when focus changes and the Done button is pressed quickly.
        pendingSpeed = parseAndClamp(speedField.getValue(), 1, 1000);
        if (doorOpenDelayField != null) pendingDoorOpenDelay = parseAndClamp(doorOpenDelayField.getValue(), 0, 60);
        if (doorCloseDelayField != null) pendingDoorCloseDelay = parseAndClamp(doorCloseDelayField.getValue(), 0, 60);

        S1MtrAddon.LOGGER.info("S1MTR save clicked: speed={} km/h, openDelay={} s, closeDelay={} s, rail={}",
                pendingSpeed, pendingDoorOpenDelay, pendingDoorCloseDelay, RailSettings.describeRail(rail));

        final Rail updatedRail = RailSettings.copyWithCustomParams(
                rail, pendingSpeed, pendingDoorOpenDelay, pendingDoorCloseDelay);
        if (updatedRail == rail) {
            statusMessage = Component.translatable("gui.s1mtr.save_build_failed");
            S1MtrAddon.LOGGER.error("Rail update produced the original object; not sending an ineffective update");
            return;
        }

        if (MtrNetworkBridge.sendRailUpdate(updatedRail)) {
            S1MtrAddon.LOGGER.info("Sent S1MTR rail update to the server; closing the stale MTR editor");
            // Do not return to the old RailModifierScreen. It still owns the
            // pre-update Rail instance and can display or submit stale values.
            Minecraft.getInstance().setScreen(null);
        } else {
            statusMessage = Component.translatable("gui.s1mtr.save_send_failed");
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        final int panelWidth = Math.min(300, width - 30);
        final int x = (width - panelWidth) / 2;
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.s1mtr.speed"), x, 64, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("gui.s1mtr.speed_hint"), x, 78, 0xA0A0A0);
        if (doorOpenDelayField != null) {
            graphics.drawString(font, Component.translatable("gui.s1mtr.door_open_delay"), x, 102, 0xFFFFFF);
            graphics.drawString(font, Component.translatable("gui.s1mtr.door_close_delay"), x, 140, 0xFFFFFF);
        }
        if (statusMessage != null) {
            graphics.drawCenteredString(font, statusMessage, width / 2, height - 66, 0xFF7777);
        }
    }
}
