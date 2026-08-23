package top.s1metro.s1mtr.client.builder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;

/**
 * 复合构建器的剖面数据模型。
 * <p>
 * 14×14 二维网格,每个格子存储一个方块的 BlockState 字符串:
 * <ul>
 *   <li>{@code null}:不操作(放样时跳过)</li>
 *   <li>{@code "minecraft:air"}:放样时放置空气(挖空)</li>
 *   <li>{@code "minecraft:stone"}:默认状态方块</li>
 *   <li>{@code "minecraft:oak_stairs[facing=north,half=top,waterlogged=true]"}:完整 BlockState</li>
 * </ul>
 * 网格坐标以轨道节点为 (0,0):x 轴负方向为节点左边(玩家视角),
 * y 轴正方向为上方。数组下标 index = coord + 7,即坐标范围 x/y ∈ [-7, 6]。
 * <p>
 * 序列化格式:14×14 个格子用 {@code |} 分隔,空串表示不操作。
 * 旧格式 {@code blockId@rotation} 在反序列化时 rotation 信息被忽略(转为纯 blockId)。
 * 默认剖面 (-3,0)~(3,3) 区间为空气,用于快速挖出隧道空间。
 */
public final class CompositeProfile {

	public static final int GRID_SIZE = 14;
	public static final int CENTER = GRID_SIZE / 2;

	/** 网格数据 [gy + CENTER][gx + CENTER],存储 BlockState 字符串 */
	private final String[][] cells = new String[GRID_SIZE][GRID_SIZE];

	public CompositeProfile() {
		resetDefault();
	}

	private CompositeProfile(String[][] cells) {
		for (int gy = 0; gy < GRID_SIZE; gy++) {
			System.arraycopy(cells[gy], 0, this.cells[gy], 0, GRID_SIZE);
		}
	}

	/** 恢复默认剖面:(-3,0)~(3,3) 为空气,其余不操作。 */
	public void resetDefault() {
		for (int gy = 0; gy < GRID_SIZE; gy++) {
			java.util.Arrays.fill(cells[gy], null);
		}
		for (int gx = -3; gx <= 3; gx++) {
			for (int gy = 0; gy <= 3; gy++) {
				setCell(gx, gy, "minecraft:air");
			}
		}
	}

	/**
	 * 设置某个格子的方块 BlockState 字符串。
	 *
	 * @param gx       剖面坐标 x ∈ [-7, 6]
	 * @param gy       剖面坐标 y ∈ [-7, 6]
	 * @param blockState 方块 ID 或完整 BlockState 字符串,null 表示不操作
	 */
	public void setCell(int gx, int gy, String blockState) {
		final int ix = gx + CENTER;
		final int iy = gy + CENTER;
		if (ix < 0 || ix >= GRID_SIZE || iy < 0 || iy >= GRID_SIZE) {
			return;
		}
		cells[iy][ix] = blockState;
	}

	/** 获取某个格子的 BlockState 字符串,可能为 null(不操作)。 */
	public String getCell(int gx, int gy) {
		final int ix = gx + CENTER;
		final int iy = gy + CENTER;
		if (ix < 0 || ix >= GRID_SIZE || iy < 0 || iy >= GRID_SIZE) {
			return null;
		}
		return cells[iy][ix];
	}

	/** 循环切换某格子方块的旋转(顺时针 90°)。对方块为 null/空气的格子无操作。 */
	public void cycleRotation(int gx, int gy) {
		final BlockState state = getBlockState(gx, gy);
		if (state == null) {
			return;
		}
		final BlockState rotated = state.rotate(BlockRotation.CLOCKWISE_90);
		setCell(gx, gy, blockStateToString(rotated));
	}

	/** 上下翻转:对支持 {@code half} 属性的方块(楼梯/台阶)切换 top/bottom。 */
	public void flipVertical(int gx, int gy) {
		final BlockState state = getBlockState(gx, gy);
		if (state == null) {
			return;
		}
		final net.minecraft.state.property.Property<?> halfProp = findPropertyByName(state, "half");
		if (halfProp == null) {
			return;
		}
		final BlockState flipped = cycleProperty(state, halfProp);
		setCell(gx, gy, blockStateToString(flipped));
	}

	/** 在方块状态中查找指定名字的属性。 */
	private static net.minecraft.state.property.Property<?> findPropertyByName(BlockState state, String name) {
		for (net.minecraft.state.property.Property<?> prop : state.getProperties()) {
			if (prop.getName().equals(name)) {
				return prop;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> BlockState cycleProperty(BlockState state, net.minecraft.state.property.Property<?> prop) {
		final net.minecraft.state.property.Property<T> typed = (net.minecraft.state.property.Property<T>) prop;
		final T current = state.get(typed);
		T target = current;
		boolean found = false;
		for (T v : typed.getValues()) {
			if (found) {
				target = v;
				break;
			}
			if (v.equals(current)) {
				found = true;
			}
		}
		if (!found || target == current) {
			// 当前值不在列表中或为最后一个,循环回第一个
			if (!typed.getValues().isEmpty()) {
				target = typed.getValues().iterator().next();
			}
		}
		return state.with(typed, target);
	}

	/** 解析某格子为 BlockState,失败返回 null。 */
	public BlockState getBlockState(int gx, int gy) {
		final String cell = getCell(gx, gy);
		if (cell == null || cell.isEmpty()) {
			return null;
		}
		return parseBlockState(cell);
	}

	/**
	 * 将 BlockState 序列化为字符串:简单方块用 ID,带属性用 [k=v,...] 语法。
	 * <p>
	 * 不能用 {@link BlockState#toString()},因为 1.20.1 中它返回 {@code Block{namespace:path}[k=v,...]}
	 * 格式(带 Block{} 前缀),{@link #parseBlockState} 无法解析。这里手动构建
	 * {@code namespace:path[k=v,...]} 格式,与 BlockState 字符串约定一致。
	 */
	public static String blockStateToString(BlockState state) {
		if (state == null) {
			return null;
		}
		final Identifier id = Registries.BLOCK.getId(state.getBlock());
		final StringBuilder sb = new StringBuilder(id.toString());
		final java.util.Collection<net.minecraft.state.property.Property<?>> props = state.getProperties();
		if (!props.isEmpty()) {
			sb.append('[');
			boolean first = true;
			for (net.minecraft.state.property.Property<?> prop : props) {
				if (!first) {
					sb.append(',');
				}
				sb.append(prop.getName()).append('=').append(state.get(prop));
				first = false;
			}
			sb.append(']');
		}
		return sb.toString();
	}

	/** 解析 BlockState 字符串:支持 {@code minecraft:stone} 与 {@code minecraft:chest[facing=north]} 两种形式。 */
	public static BlockState parseBlockState(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		final int bracket = text.indexOf('[');
		final String idPart = bracket >= 0 ? text.substring(0, bracket) : text;
		final Identifier id = Identifier.tryParse(idPart);
		if (id == null || !Registries.BLOCK.containsId(id)) {
			return null;
		}
		final Block block = Registries.BLOCK.get(id);
		BlockState state = block.getDefaultState();
		if (bracket >= 0 && text.endsWith("]")) {
			final String propsText = text.substring(bracket + 1, text.length() - 1);
			if (!propsText.isEmpty()) {
				for (String pair : propsText.split(",")) {
					final int eq = pair.indexOf('=');
					if (eq <= 0) {
						continue;
					}
					final String key = pair.substring(0, eq).trim();
					final String value = pair.substring(eq + 1).trim();
					state = withProperty(state, key, value);
				}
			}
		}
		return state;
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> BlockState withProperty(BlockState state, String key, String value) {
		for (net.minecraft.state.property.Property<?> prop : state.getProperties()) {
			if (prop.getName().equals(key)) {
				final net.minecraft.state.property.Property<T> typed = (net.minecraft.state.property.Property<T>) prop;
				final java.util.Optional<T> parsed = typed.parse(value);
				if (parsed.isPresent()) {
					return state.with(typed, parsed.get());
				}
				return state;
			}
		}
		return state;
	}

	/** 序列化为单行字符串(用于 styles 持久化与网络传输)。 */
	public String serialize() {
		final StringBuilder sb = new StringBuilder(GRID_SIZE * GRID_SIZE * 16);
		for (int gy = 0; gy < GRID_SIZE; gy++) {
			for (int gx = 0; gx < GRID_SIZE; gx++) {
				if (gy > 0 || gx > 0) {
					sb.append('|');
				}
				final String cell = cells[gy][gx];
				if (cell != null) {
					// | 与 [ ] , = 字符在 cell 字符串里可能存在,需要转义
					// 简单方案:替换 | 为 \p(管道符在 BlockState 字符串里不会出现)
					sb.append(cell.replace("|", "\\p"));
				}
			}
		}
		return sb.toString();
	}

	/** 从序列化字符串反序列化。旧 {@code blockId@rotation} 格式的 rotation 被忽略。 */
	public static CompositeProfile deserialize(String data) {
		final String[][] newCells = new String[GRID_SIZE][GRID_SIZE];
		final String[] parts = data.split("\\|", -1);
		for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
			final String part = i < parts.length ? parts[i] : "";
			if (part.isEmpty()) {
				newCells[i / GRID_SIZE][i % GRID_SIZE] = null;
			} else {
				String cell = part.replace("\\p", "|");
				// 兼容旧 blockId@rotation 格式:rotation 信息被忽略
				final int at = cell.indexOf('@');
				if (at >= 0 && !cell.contains("[")) {
					cell = cell.substring(0, at);
				}
				newCells[i / GRID_SIZE][i % GRID_SIZE] = cell;
			}
		}
		return new CompositeProfile(newCells);
	}

	/** 复制当前剖面。 */
	public CompositeProfile copy() {
		return new CompositeProfile(cells);
	}

	/** 用另一个剖面的内容覆盖当前剖面(用于加载预制剖面)。 */
	public void copyFrom(CompositeProfile other) {
		if (other == null) {
			return;
		}
		for (int gy = 0; gy < GRID_SIZE; gy++) {
			System.arraycopy(other.cells[gy], 0, cells[gy], 0, GRID_SIZE);
		}
	}

	/** 判断某格子是否为空气(挖空)。 */
	public static boolean isAirCell(String cell) {
		return "minecraft:air".equals(cell);
	}
}
