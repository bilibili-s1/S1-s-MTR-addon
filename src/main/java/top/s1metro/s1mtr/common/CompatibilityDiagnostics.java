package top.s1metro.s1mtr.common;

import top.s1metro.s1mtr.S1MtrAddon;

public final class CompatibilityDiagnostics {
    private CompatibilityDiagnostics() {
    }

    public static void run() {
        try {
            final Class<?> rail = Class.forName("org.mtr.core.data.Rail", false, CompatibilityDiagnostics.class.getClassLoader());
            S1MtrAddon.LOGGER.info("MTR Rail API detected: {} constructors, {} methods", rail.getDeclaredConstructors().length, rail.getDeclaredMethods().length);
        } catch (Throwable throwable) {
            S1MtrAddon.LOGGER.error("MTR Rail API is unavailable", throwable);
        }
        S1MtrAddon.LOGGER.info("S1MTR 1.2.3 uses NeoForge ScreenEvent.Init.Post instead of MTR screen mixins");
    }
}
