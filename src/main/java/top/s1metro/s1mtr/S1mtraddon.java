package top.s1metro.s1mtr;

import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class S1mtraddon implements ModInitializer {
	public static final String MOD_ID = "s1mtraddon";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello from s1metro team!");
		LOGGER.info("S1 MTR Addon initializing...");
		LOGGER.info("MTR dependency detected - Ready to extend Minecraft Transit Railway!");
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
