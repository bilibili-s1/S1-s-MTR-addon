package top.s1metro.s1mtr.service;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 侧线「列车自动驾驶限速」的权威存储（服务端）。
 * <p>
 * 与 MTR 的 {@code Siding.maxManualSpeed}（手动驾驶限速）完全独立：
 * <ul>
 *   <li>UI 输入框读写 {@link SidingSpeedStore}（客户端显示层）；</li>
 *   <li>保存时通过 C2S 包把值写到本类（服务端执行层），持久化到服务端 config；</li>
 *   <li>{@code VehicleSidingSpeedLimitMixin} 在服务端模拟时按 siding id 从这里读取限速。</li>
 * </ul>
 * 因此限速不会跟着手动驾驶滑块变，且专用服/单人服都可用。
 */
public final class SidingAutoSpeedLimit {

	private static final Path FILE = FabricLoader.getInstance().getConfigDir()
			.resolve("s1mtr").resolve("siding_auto_speeds.properties");
	private static final ConcurrentHashMap<Long, Double> LIMITS = new ConcurrentHashMap<>();

	static {
		try {
			if (Files.exists(FILE)) {
				final Properties props = new Properties();
				try (java.io.Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
					props.load(reader);
				}
				for (String key : props.stringPropertyNames()) {
					try {
						LIMITS.put(Long.parseLong(key), Double.parseDouble(props.getProperty(key)));
					} catch (NumberFormatException ignored) {
					}
				}
			}
		} catch (IOException ignored) {
		}
	}

	private SidingAutoSpeedLimit() {
	}

	/** 读取侧线自动驾驶限速（km/h）；0 = 不限速。 */
	public static double get(long sidingId) {
		final Double value = LIMITS.get(sidingId);
		return value == null ? 0 : value;
	}

	/** 服务端：设置并持久化；kmh<=0 或 >=1000 存 0（不限速）。 */
	public static synchronized void set(long sidingId, double kmh) {
		final double value = (kmh > 0 && kmh < 1000) ? kmh : 0;
		if (value == 0) {
			LIMITS.remove(sidingId);
		} else {
			LIMITS.put(sidingId, value);
		}
		try {
			Files.createDirectories(FILE.getParent());
			final Properties props = new Properties();
			for (java.util.Map.Entry<Long, Double> entry : LIMITS.entrySet()) {
				props.setProperty(Long.toString(entry.getKey()), Double.toString(entry.getValue()));
			}
			try (java.io.Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				props.store(writer, "S1MTR siding AUTO speed limits (km/h; 0 = no limit)");
			}
		} catch (IOException ignored) {
		}
	}
}
