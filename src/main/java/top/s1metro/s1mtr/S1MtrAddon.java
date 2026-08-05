package top.s1metro.s1mtr;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.s1metro.s1mtr.common.CompatibilityDiagnostics;

@Mod(S1MtrAddon.MOD_ID)
public final class S1MtrAddon {
    public static final String MOD_ID = "s1mtraddon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public S1MtrAddon() {
        LOGGER.info("S1 MTR Addon NeoForge port initializing");
        CompatibilityDiagnostics.run();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
