package top.s1metro.s1mtr.client;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Rail.Shape;
import org.mtr.core.data.Position;
import org.mtr.core.data.RailMath;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mod.block.BlockNode;
import top.s1metro.s1mtr.client.builder.CompositeProfile;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;
import top.s1metro.s1mtr.mixin.RailSchemaAccessor;

import java.lang.reflect.Constructor;

public final class RailSpeedHelper {

	private static final String DOOR_OPEN_DELAY_PREFIX = "s1mtr:doorOpenDelay=";
	private static final String DOOR_CLOSE_DELAY_PREFIX = "s1mtr:doorCloseDelay=";
	/** 站台开门方向:0=默认(自动) 1=开左门 2=开右门 3=开双门 4=不开门。 */
	private static final String OPENING_DIRECTION_PREFIX = "s1mtr:openingDirection=";
	public static final String COMPOSITE_PROFILE_PREFIX = "s1mtr:compositeProfile=";
	public static final String COMPOSITE_SCHEDULE_PREFIX = "s1mtr:compositeSchedule=";

	private RailSpeedHelper() {
	}

	private static volatile Constructor<Rail> RAIL_CONSTRUCTOR;

	private static Constructor<Rail> getRailConstructor() throws ReflectiveOperationException {
		if (RAIL_CONSTRUCTOR != null) {
			return RAIL_CONSTRUCTOR;
		}
		for (Constructor<?> constructor : Rail.class.getDeclaredConstructors()) {
			Class<?>[] params = constructor.getParameterTypes();
			if (params.length >= 7 &&
					params[0] == Position.class &&
					params[1] == Angle.class &&
					params[2] == Position.class &&
					params[3] == Angle.class &&
					params[4] == Shape.class &&
					params[5] == double.class &&
					params[6] == ObjectArrayList.class) {
				constructor.setAccessible(true);
				RAIL_CONSTRUCTOR = (Constructor<Rail>) constructor;
				return RAIL_CONSTRUCTOR;
			}
		}
		throw new NoSuchMethodException("Cannot find Rail constructor");
	}

	public static Rail copyWithCustomParams(Rail original, Shape shape, double radius,
			ObjectArrayList<String> styles, long speed, long doorOpenDelay, long doorCloseDelay) {
		RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) original;

		ObjectArrayList<String> newStyles = new ObjectArrayList<>(styles);
		removeDelayStyles(newStyles);
		if (doorOpenDelay > 0) {
			newStyles.add(DOOR_OPEN_DELAY_PREFIX + doorOpenDelay);
		}
		if (doorCloseDelay > 0) {
			newStyles.add(DOOR_CLOSE_DELAY_PREFIX + doorCloseDelay);
		}

		long speedLimit1 = (accessor.s1mtr$getSpeedLimit1() == 0) ? 0L : speed;
		long speedLimit2 = speed;

		try {
			return getRailConstructor().newInstance(
					accessor.s1mtr$getPosition1(), accessor.s1mtr$getAngle1(),
					accessor.s1mtr$getPosition2(), accessor.s1mtr$getAngle2(),
					shape, radius, newStyles,
					speedLimit1, speedLimit2,
					accessor.s1mtr$isPlatform(), accessor.s1mtr$isSiding(), accessor.s1mtr$canAccelerate(),
					accessor.s1mtr$canTurnBack(), accessor.s1mtr$canConnectRemotely(), accessor.s1mtr$canHaveSignal(),
					accessor.s1mtr$getTransportMode());
		} catch (ReflectiveOperationException e) {
			return Rail.copy(original, newStyles);
		}
	}

	private static void removeDelayStyles(ObjectArrayList<String> styles) {
		for (int i = styles.size() - 1; i >= 0; i--) {
			String style = styles.get(i);
			if (style.startsWith(DOOR_OPEN_DELAY_PREFIX) || style.startsWith(DOOR_CLOSE_DELAY_PREFIX)) {
				styles.remove(i);
			}
		}
	}

	/**
	 * 从轨道的 styles 中读取已保存的分层调度表;若无则返回空调度表(单层默认剖面)。
	 * <p>
	 * 兼容旧 {@code compositeProfile=} 单剖面格式,转为单层无限长度的 schedule。
	 */
	public static CompositeLayerSchedule getCompositeSchedule(Rail rail) {
		ObjectArrayList<String> styles = getStyles(rail);
		for (String style : styles) {
			if (style.startsWith(COMPOSITE_SCHEDULE_PREFIX)) {
				try {
					return CompositeLayerSchedule.deserialize(style.substring(COMPOSITE_SCHEDULE_PREFIX.length()));
				} catch (Exception ignored) {
				}
			}
		}
		// 兼容旧单剖面格式
		for (String style : styles) {
			if (style.startsWith(COMPOSITE_PROFILE_PREFIX)) {
				try {
					final CompositeProfile profile = CompositeProfile.deserialize(style.substring(COMPOSITE_PROFILE_PREFIX.length()));
					final CompositeLayerSchedule schedule = new CompositeLayerSchedule();
					schedule.entries().get(0).profile = profile;
					schedule.entries().get(0).length = 1;
					return schedule;
				} catch (Exception ignored) {
				}
			}
		}
		return new CompositeLayerSchedule();
	}

	/**
	 * 从轨道的 styles 中读取已保存的复合剖面(取第一层);若无则返回默认剖面。
	 * <p>
	 * 兼容旧版直接读 compositeProfile= 的逻辑。
	 */
	public static CompositeProfile getCompositeProfile(Rail rail) {
		return getCompositeSchedule(rail).entries().get(0).profile;
	}

	/** 轨道是否保存过分层调度表或旧的复合剖面条目。 */
	public static boolean hasCompositeProfile(Rail rail) {
		ObjectArrayList<String> styles = getStyles(rail);
		for (String style : styles) {
			if (style.startsWith(COMPOSITE_SCHEDULE_PREFIX) || style.startsWith(COMPOSITE_PROFILE_PREFIX)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 样式选择等场景会整体替换轨道的 styles,此方法在复制结果上重新写入原轨道的分层调度表,
	 * 避免条目丢失。原轨道无调度表时原样返回。
	 */
	public static Rail preserveCompositeProfile(Rail original, Rail copy) {
		if (hasCompositeProfile(original)) {
			return copyWithCompositeSchedule(copy, getCompositeSchedule(original));
		}
		return copy;
	}

	/** 复制轨道并写入单层剖面(向后兼容入口,内部转为单层 schedule)。 */
	public static Rail copyWithCompositeProfile(Rail original, CompositeProfile profile) {
		final CompositeLayerSchedule schedule = new CompositeLayerSchedule();
		if (profile != null) {
			schedule.entries().get(0).profile = profile;
			schedule.entries().get(0).length = 1;
		}
		return copyWithCompositeSchedule(original, schedule);
	}

	/**
	 * 复制轨道并写入分层调度表(以 styles 条目持久化),同时保留原速度/开关门延迟参数。
	 */
	public static Rail copyWithCompositeSchedule(Rail original, CompositeLayerSchedule schedule) {
		RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) original;

		ObjectArrayList<String> newStyles = new ObjectArrayList<>(accessor.s1mtr$getStyles());
		for (int i = newStyles.size() - 1; i >= 0; i--) {
			final String s = newStyles.get(i);
			if (s.startsWith(COMPOSITE_SCHEDULE_PREFIX) || s.startsWith(COMPOSITE_PROFILE_PREFIX)) {
				newStyles.remove(i);
			}
		}
		if (schedule != null) {
			newStyles.add(COMPOSITE_SCHEDULE_PREFIX + schedule.serialize());
		}

		long speed = getSpeedLimit2(original);
		if (speed <= 0) {
			speed = getSpeedLimit1(original);
		}

		return copyWithCustomParams(
				original,
				accessor.s1mtr$getShape(),
				accessor.s1mtr$getVerticalRadius(),
				newStyles,
				speed,
				getDoorOpenDelay(original) / 1000,
				getDoorCloseDelay(original) / 1000);
	}

	public static long getSpeedLimit1(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getSpeedLimit1();
	}

	public static long getSpeedLimit2(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getSpeedLimit2();
	}

	public static ObjectArrayList<String> getStyles(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getStyles();
	}

	public static Shape getShape(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getShape();
	}

	public static double getRadius(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$getVerticalRadius();
	}

	public static boolean isPlatform(Rail rail) {
		return ((RailSchemaAccessor) (Object) rail).s1mtr$isPlatform();
	}

	public static long getDoorOpenDelay(Rail rail) {
		ObjectArrayList<String> styles = ((RailSchemaAccessor) (Object) rail).s1mtr$getStyles();
		for (String style : styles) {
			if (style.startsWith(DOOR_OPEN_DELAY_PREFIX)) {
				try {
					return Long.parseLong(style.substring(DOOR_OPEN_DELAY_PREFIX.length())) * 1000;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return 0;
	}

	public static long getDoorCloseDelay(Rail rail) {
		ObjectArrayList<String> styles = ((RailSchemaAccessor) (Object) rail).s1mtr$getStyles();
		for (String style : styles) {
			if (style.startsWith(DOOR_CLOSE_DELAY_PREFIX)) {
				try {
					return Long.parseLong(style.substring(DOOR_CLOSE_DELAY_PREFIX.length())) * 1000;
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return 0;
	}

	/** 站台开门方向:0=默认(自动) 1=左 2=右 3=双 4=不开。默认 0。 */
	public static int getOpeningDirection(Rail rail) {
		ObjectArrayList<String> styles = ((RailSchemaAccessor) (Object) rail).s1mtr$getStyles();
		for (String style : styles) {
			if (style.startsWith(OPENING_DIRECTION_PREFIX)) {
				try {
					return Integer.parseInt(style.substring(OPENING_DIRECTION_PREFIX.length()));
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return 0;
	}

	/** 设置站台开门方向，写入轨道 styles。 */
	public static Rail setOpeningDirection(Rail rail, int direction) {
		final ObjectArrayList<String> newStyles = new ObjectArrayList<>(
				((RailSchemaAccessor) (Object) rail).s1mtr$getStyles());
		for (int i = newStyles.size() - 1; i >= 0; i--) {
			if (newStyles.get(i).startsWith(OPENING_DIRECTION_PREFIX)) {
				newStyles.remove(i);
			}
		}
		if (direction > 0) {
			newStyles.add(OPENING_DIRECTION_PREFIX + Math.min(4, Math.max(0, direction)));
		}
		return copyWithCustomParams(
				rail,
				((RailSchemaAccessor) (Object) rail).s1mtr$getShape(),
				((RailSchemaAccessor) (Object) rail).s1mtr$getVerticalRadius(),
				newStyles,
				getSpeedLimit2(rail) > 0 ? getSpeedLimit2(rail) : getSpeedLimit1(rail),
				getDoorOpenDelay(rail) / 1000,
				getDoorCloseDelay(rail) / 1000);
	}

	public static long calculateRecommendedSpeed(Rail rail) {
		RailSchemaAccessor accessor = (RailSchemaAccessor) (Object) rail;

		boolean isPlatform = accessor.s1mtr$isPlatform();
		boolean isSiding = accessor.s1mtr$isSiding();
		boolean canTurnBack = accessor.s1mtr$canTurnBack();

		if (isPlatform || isSiding || canTurnBack) {
			return 0;
		}

		Position pos1 = accessor.s1mtr$getPosition1();
		Position pos2 = accessor.s1mtr$getPosition2();

		double dx = pos2.getX() - pos1.getX();
		double dy = pos2.getY() - pos1.getY();
		double dz = pos2.getZ() - pos1.getZ();

		double fullLength = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (fullLength <= 0) {
			return -1;
		}

		double slope = Math.abs(dy) / fullLength;

		RailMath railMath = new RailMath(
				pos1, accessor.s1mtr$getAngle1(),
				pos2, accessor.s1mtr$getAngle2(),
				accessor.s1mtr$getShape(),
				accessor.s1mtr$getVerticalRadius()
		);

		DoubleDoubleImmutablePair radii = railMath.getHorizontalRadii();
		double r1 = radii.firstDouble();
		double r2 = radii.secondDouble();

		double minHorizontalRadius = r1 > 0 && r2 > 0 ? Math.min(r1, r2) : (r1 > 0 ? r1 : (r2 > 0 ? r2 : -1));

		long curveSpeed = calculateCurveSpeed(minHorizontalRadius);
		long slopeSpeed = calculateSlopeSpeed(slope);

		if (curveSpeed < 0 && slopeSpeed < 0) {
			return -1;
		} else if (curveSpeed < 0) {
			return slopeSpeed;
		} else if (slopeSpeed < 0) {
			return curveSpeed;
		} else {
			return Math.min(curveSpeed, slopeSpeed);
		}
	}

	/**
	 * 弯道限速(连续公式,按 5km/h 级别细化)。
	 * <p>
	 * 基于铁路侧向加速度经验值:限速 v = sqrt(a * R) (a≈0.7 m/s²),换算 km/h 后随半径连续变化,
	 * 半径越大限速越高,自然呈现约每 5 km/h 一档的细腻梯度。
	 */
	private static long calculateCurveSpeed(double radius) {
		if (radius <= 0) {
			return -1;
		}
		// v(m/s) = sqrt(0.7 * R), v(km/h) = v * 3.6
		final double vMs = Math.sqrt(0.7 * radius);
		final long vKmh = Math.round(vMs * 3.6);
		// 直线段(R 极大)封顶,避免无限制
		return Math.min(vKmh, 320);
	}

	/**
	 * 坡度限速(连续公式,按 5km/h 级别细化)。
	 * <p>
	 * 基于坡度经验式:限速随坡度增大而下降(约 v_kmh ≈ 90 * sqrt(0.003 / slope))。
	 * 坡度越小限速越高,自然呈现约每 5 km/h 一档的细腻梯度。
	 */
	private static long calculateSlopeSpeed(double slope) {
		if (slope < 0.0005) {
			return -1; // 近乎平坡,坡度不限制,由弯道/默认上限决定
		}
		final double vKmh = 90 * Math.sqrt(0.003 / slope);
		return (long) Math.min(vKmh, 320);
	}

	/**
	 * 根据两个轨道节点位置估算推荐限速(km/h)。
	 * <p>
	 * 这是自动速度连接器、HUD 预览、R 键自动填表共用的统一入口。
	 * 由于连接前只有两个端点(无法预知中间曲率),限速以<b>坡度</b>为主(两端高差/长度),
	 * 曲率部分取上限不限制;结果由调用方 clamp 到配置的最高速度。
	 *
	 * @param position1 第一节点
	 * @param position2 第二节点
	 * @return 推荐限速(km/h);无法计算(两点重合)时返回 -1
	 */
	public static long recommendFor(Position position1, Position position2) {
		if (position1 == null || position2 == null || position1.equals(position2)) {
			return -1;
		}

		final double dx = position2.getX() - position1.getX();
		final double dy = position2.getY() - position1.getY();
		final double dz = position2.getZ() - position1.getZ();
		final double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length <= 0) {
			return -1;
		}

		// 坡度限速(连续公式),曲线部分无法从两端点预知,取上限不限制
		final double slope = Math.abs(dy) / length;
		final long slopeSpeed = calculateSlopeSpeed(slope);
		if (slopeSpeed < 0) {
			// 平坡:坡度不限制,返回 -1 表示"无坡度限制,由调用方使用配置上限"
			return -1;
		}
		return Math.min(slopeSpeed, 320);
	}

	/**
	 * HUD 预览用的推荐限速(km/h)，含<b>弯道曲率</b>。
	 * <p>
	 * 由于预览时第二点可能是普通方块（虚拟节点，无真实朝向），而轨道节点才携带朝向，
	 * 因此分两种情形：
	 * <ul>
	 *   <li>第二点是轨道节点：用两节点真实朝向经 {@link Rail#getAngles} 得到端部角度，
	 *       构造临时轨道以反映真实曲率；</li>
	 *   <li>第二点不是节点：按直线方向构造两端 180° 互补角度（无曲率，仅坡度限速）。</li>
	 * </ul>
	 *
	 * @param first      第一节点（已选点，必须为轨道节点）
	 * @param second     候选第二点（已按规则换算为节点或其上一格）
	 * @param world      客户端世界
	 * @return 推荐限速(km/h)；无法计算(重合/无法构成轨道)时返回 -1
	 */
	public static long recommendForPreview(Position first, BlockPos second, ClientWorld world) {
		if (first == null || second == null || world == null) {
			return -1;
		}
		final Position secondPos = new Position(second.getX(), second.getY(), second.getZ());
		if (first.equals(secondPos)) {
			return -1;
		}

		final net.minecraft.block.BlockState secondStateV = world.getBlockState(second);
		final boolean isNode = secondStateV.getBlock() instanceof BlockNode;

		// 第一点一定是轨道节点（由连接器选点逻辑保证）
		final org.mtr.mapping.holder.BlockState firstStateV =
				new org.mtr.mapping.holder.BlockState(world.getBlockState(new BlockPos((int) first.getX(), (int) first.getY(), (int) first.getZ())));

		final float angle1F = BlockNode.getAngle(firstStateV);
		final float angle2F;
		if (isNode) {
			angle2F = BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(secondStateV));
		} else {
			// 虚拟节点无朝向：取第一点朝向，得到直线
			angle2F = angle1F;
		}

		final ObjectObjectImmutablePair<Angle, Angle> angles =
				Rail.getAngles(first, angle1F, secondPos, angle2F);

		final ObjectArrayList<String> styles = new ObjectArrayList<>();
		styles.add("default");
		final Rail probe = Rail.newRail(
				first, angles.left(), secondPos, angles.right(),
				Shape.QUADRATIC, 0, styles,
				1, 1, false, false, true, false, false, TransportMode.TRAIN);
		if (!probe.isValid()) {
			return -1;
		}
		return calculateRecommendedSpeed(probe);
	}
}