package top.s1metro.s1mtr.service;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 侧线列车限速的「独立 UI 存储」。
 * <p>
 * 背景：MTR 的 {@code Siding.maxManualSpeed} 是手动驾驶限速，SidingScreen 里的滑块也读它。
 * 如果我们的输入框直接显示/写入这个字段，就会和手动限速滑块同步、且被 MTR 界面限制为只读。
 * 因此单独用本类按侧线 ID 存储玩家设置的列车限速（km/h），UI 读写这里；
 * 保存时再同步到 {@code maxManualSpeed} 作为执行层（实测列车限速真正生效的来源）。
 * <p>
 * 文件：{@code config/s1mtr/siding_speeds.properties}。0 表示不限速。
 */
public final class SidingSpeedStore {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve("s1mtr").resolve("siding_speeds.properties");
	private static final Properties PROPS = new Properties();

	static {
		try {
			if (Files.exists(FILE)) {
				try (java.io.Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
					PROPS.load(reader);
				}
			}
		} catch (IOException ignored) {
		}
	}

	private SidingSpeedStore() {
	}

	/** 读取侧线限速（km/h）；未设置返回 0（不限速）。 */
	public static double get(long sidingId) {
		try {
			return Double.parseDouble(PROPS.getProperty(Long.toString(sidingId), "0"));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	/** 设置并持久化；kmh<=0 或 >=1000 视为不限速，写 0。 */
	public static void set(long sidingId, double kmh) {
		final double value = (kmh > 0 && kmh < 1000) ? kmh : 0;
		PROPS.setProperty(Long.toString(sidingId), Double.toString(value));
		try {
			Files.createDirectories(FILE.getParent());
			try (java.io.Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				PROPS.store(writer, "S1MTR siding train speed limits (km/h; 0 = no limit)");
			}
		} catch (IOException ignored) {
		}
	}
}
