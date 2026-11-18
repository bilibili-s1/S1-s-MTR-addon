package top.s1metro.s1mtr.api;

import org.mtr.mod.data.RailType;

import java.util.Map;
import java.util.TreeMap;

/**
 * 轨道限速颜色 API —— {@code MTR_DEFAULT} 配色（移植自 magic-mtr，MIT）。
 * <p>
 * 档位颜色直接读取当前 MTR 版本的 {@link RailType#speedLimit} / {@link RailType#color}，
 * 因此与 MTR 4.0.4 / 4.0.5 自动保持一致；magic-mtr 额外钉死了两档：
 * <ul>
 *   <li>250 km/h → {@code 0xFFB87333}（铜色）</li>
 *   <li>400 km/h → {@code 0xFFB42AE6}（紫珀连接器，品红）</li>
 * </ul>
 * 中间速度按档位线性插值，返回 ARGB（alpha 恒为 0xFF）。
 */
public final class RailSpeedColorApi {

	public static final int COPPER_250_COLOR = 0xFFB87333;
	public static final int PURPUR_400_COLOR = 0xFFB42AE6;
	public static final int MAX_SPEED_KMH = 500;

	private static final int[] SPEEDS;
	private static final int[] COLORS;

	static {
		final TreeMap<Integer, Integer> bySpeed = new TreeMap<>();
		for (final RailType type : new RailType[]{
				RailType.WOODEN, RailType.STONE, RailType.EMERALD, RailType.IRON,
				RailType.BRICKS, RailType.OBSIDIAN, RailType.PRISMARINE,
				RailType.BLAZE, RailType.QUARTZ, RailType.DIAMOND}) {
			final int speed = Math.max(1, Math.min(MAX_SPEED_KMH, type.speedLimit));
			bySpeed.put(speed, 0xFF000000 | (type.color & 0xFFFFFF));
		}
		bySpeed.put(250, COPPER_250_COLOR);
		bySpeed.put(400, PURPUR_400_COLOR);

		SPEEDS = new int[bySpeed.size()];
		COLORS = new int[bySpeed.size()];
		int i = 0;
		for (Map.Entry<Integer, Integer> entry : bySpeed.entrySet()) {
			SPEEDS[i] = entry.getKey();
			COLORS[i] = entry.getValue();
			i++;
		}
	}

	private RailSpeedColorApi() {
	}

	/** 按速度（km/h）返回 MTR_DEFAULT 配色的 ARGB 颜色。 */
	public static int getColor(int speedKmh) {
		if (speedKmh <= SPEEDS[0]) {
			return COLORS[0];
		}
		if (speedKmh >= SPEEDS[SPEEDS.length - 1]) {
			return COLORS[COLORS.length - 1];
		}
		for (int i = 0; i < SPEEDS.length - 1; i++) {
			if (speedKmh >= SPEEDS[i] && speedKmh <= SPEEDS[i + 1]) {
				final int s0 = SPEEDS[i], s1 = SPEEDS[i + 1];
				return lerp(COLORS[i], COLORS[i + 1], (double) (speedKmh - s0) / (s1 - s0));
			}
		}
		return COLORS[COLORS.length - 1];
	}

	private static int lerp(int c0, int c1, double t) {
		final int a = (c0 >>> 24) & 0xFF;
		final int r = (c0 >>> 16) & 0xFF;
		final int g = (c0 >>> 8) & 0xFF;
		final int b = c0 & 0xFF;
		final int a2 = (c1 >>> 24) & 0xFF;
		final int r2 = (c1 >>> 16) & 0xFF;
		final int g2 = (c1 >>> 8) & 0xFF;
		final int b2 = c1 & 0xFF;
		return ((int) Math.round(a + (a2 - a) * t) << 24)
				| ((int) Math.round(r + (r2 - r) * t) << 16)
				| ((int) Math.round(g + (g2 - g) * t) << 8)
				| (int) Math.round(b + (b2 - b) * t);
	}
}
