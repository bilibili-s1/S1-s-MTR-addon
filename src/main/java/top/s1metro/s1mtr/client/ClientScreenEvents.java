package top.s1metro.s1mtr.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.mtr.core.data.Rail;
import top.s1metro.s1mtr.S1MtrAddon;
import top.s1metro.s1mtr.client.screen.RailAdvancedSettingsScreen;
import top.s1metro.s1mtr.common.ReflectionUtil;

@EventBusSubscriber(modid = S1MtrAddon.MOD_ID, value = Dist.CLIENT)
public final class ClientScreenEvents {
    private static final String RAIL_MODIFIER_SCREEN = "org.mtr.screen.RailModifierScreen";
    private static final String RAIL_STYLE_SCREEN = "org.mtr.screen.RailStyleSelectorScreen";

    private ClientScreenEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        final Screen screen = event.getScreen();
        final String className = screen.getClass().getName();
        if (!RAIL_MODIFIER_SCREEN.equals(className) && !RAIL_STYLE_SCREEN.equals(className)) {
            return;
        }

        final int buttonWidth = 176;
        final int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        final Button button = Button.builder(
                        Component.translatable("gui.s1mtr.advanced_settings"),
                        ignored -> openAdvancedSettings(screen)
                )
                .bounds(Math.max(4, screenWidth - buttonWidth - 4), 4, buttonWidth, 20)
                .build();
        event.addListener(button);
        S1MtrAddon.LOGGER.info("Injected S1MTR advanced-settings button into {}", className);
    }

    private static void openAdvancedSettings(Screen parent) {
        final Rail rail = ReflectionUtil.findFirstValueDeep(parent, Rail.class, 4);
        if (rail == null) {
            S1MtrAddon.LOGGER.error("S1MTR button was clicked, but no MTR Rail was found in {}. Fields: {}",
                    parent.getClass().getName(), ReflectionUtil.describeFields(parent));
            return;
        }
        S1MtrAddon.LOGGER.info("Opening S1MTR advanced settings for rail {}", rail.getClass().getName());
        Minecraft.getInstance().setScreen(new RailAdvancedSettingsScreen(parent, rail));
    }
}
