package top.s1metro.s1mtr.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组全局配置。
 * <p>
 * 配置持久化到 {@code config/s1mtr/config.json}。未安装 Mod Menu 时玩家也可直接手改该文件。
 * 启动时由 {@link top.s1metro.s1mtr.S1mtraddon#onInitialize} 加载,缺失或格式错误时回退到默认值。
 */
public final class S1mtrConfig {

	/** 自动速度轨道连接器允许的最高速度(km/h)。连接时推荐速度会被 clamp 到此值。默认 80。 */
	public int autoConnectorMaxSpeed = 80;

	/** 放样时每 tick 最多写入的方块数。性能好的机器可上调,低端机可下调。范围 1-1024,默认 256。 */
	public int placementPerTick = 256;

	private static final S1mtrConfig INSTANCE = new S1mtrConfig();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private S1mtrConfig() {
	}

	public static S1mtrConfig get() {
		return INSTANCE;
	}

	/** 从 config/s1mtr/config.json 加载;文件不存在或解析失败时保留默认值。 */
	public static void load() {
		final Path path = configPath();
		if (!Files.exists(path)) {
			save();
			return;
		}
		try {
			final String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			final S1mtrConfig parsed = GSON.fromJson(json, S1mtrConfig.class);
			if (parsed != null) {
				INSTANCE.autoConnectorMaxSpeed = clampInt(parsed.autoConnectorMaxSpeed, 1, 1000, 80);
				INSTANCE.placementPerTick = clampInt(parsed.placementPerTick, 1, 1024, 256);
			}
		} catch (Exception e) {
			top.s1metro.s1mtr.S1mtraddon.LOGGER.warn("无法读取 s1mtr 配置,使用默认值: {}", e.getMessage());
		}
	}

	public static void save() {
		try {
			final Path path = configPath();
			if (!Files.exists(path.getParent())) {
				Files.createDirectories(path.getParent());
			}
			Files.write(path, GSON.toJson(INSTANCE).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			top.s1metro.s1mtr.S1mtraddon.LOGGER.warn("无法写入 s1mtr 配置: {}", e.getMessage());
		}
	}

	/** 运行时供 PlacementQueue 读取的每 tick 限额(对配置做边界保护)。 */
	public static int placementPerTick() {
		return clampInt(INSTANCE.placementPerTick, 1, 1024, 256);
	}

	/** 运行时供自动连接器读取的最高限速(对配置做边界保护)。 */
	public static int autoConnectorMaxSpeed() {
		return clampInt(INSTANCE.autoConnectorMaxSpeed, 1, 1000, 80);
	}

	private static int clampInt(int value, int min, int max, int fallback) {
		if (value < min || value > max) {
			return fallback;
		}
		return value;
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("s1mtr").resolve("config.json");
	}
}
