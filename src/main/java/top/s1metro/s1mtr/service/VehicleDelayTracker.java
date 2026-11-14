package top.s1metro.s1mtr.service;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 记录"人为引入的时间损耗",用于让 MTR 不把这些损耗算作晚点。
 * <p>
 * MTR 用 {@code Vehicle.deviation}(实际用时 - 计划用时)判定列车是否晚点,
 * 晚点后会触发"追点加速"(deviationSpeedAdjustment)。本模组有两处会人为拖慢列车：
 * <ul>
 *   <li>站台发车延迟(开门/关门延迟)——属于运营需要,不应算作晚点;</li>
 *   <li>侧线限速——属于运营需要,不应算作晚点。</li>
 * </ul>
 * 这里按列车记录累计损耗毫秒数,在 MTR 计算晚点时扣除,使列车行为与"没有这些限制"时一致。
 * <p>
 * 使用 {@link WeakHashMap} 避免列车对象被回收后残留键值。
 */
public final class VehicleDelayTracker {

	private static final Map<Object, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

	/** 单列车的人工损耗状态。 */
	private static final class State {
		long delayMs;
		long departureIndex = Long.MIN_VALUE;
		long sidingDepartureTime = Long.MIN_VALUE;
	}

	private VehicleDelayTracker() {
	}

	/**
	 * 累加某列车的人为时间损耗。
	 *
	 * @param vehicle 列车对象(可为空)
	 * @param millis  损耗毫秒数(负数按 0 处理)
	 */
	public static void add(Object vehicle, long millis) {
		if (vehicle == null || millis <= 0) {
			return;
		}
		synchronized (STATES) {
			STATES.computeIfAbsent(vehicle, key -> new State()).delayMs += millis;
		}
	}

	/**
	 * 读取某列车累计的人为时间损耗。
	 *
	 * @param vehicle 列车对象(可为空)
	 * @return 累计损耗毫秒数
	 */
	public static long get(Object vehicle) {
		if (vehicle == null) {
			return 0L;
		}
		synchronized (STATES) {
			final State state = STATES.get(vehicle);
			return state == null ? 0L : state.delayMs;
		}
	}

	/**
	 * 检查列车是否开始了新的一轮运行；若是则清空累计损耗。
	 * <p>
	 * MTR 的晚点(偏差)是相对"本次发车时间"计算的,列车回到车辆段后重新发车时
	 * 需要把上一轮累计的损耗清零,否则会多扣。
	 *
	 * @param vehicle            列车对象
	 * @param departureIndex     本次发车序号
	 * @param sidingDepartureTime 本次发车时间
	 */
	public static void checkRun(Object vehicle, long departureIndex, long sidingDepartureTime) {
		if (vehicle == null) {
			return;
		}
		synchronized (STATES) {
			final State state = STATES.computeIfAbsent(vehicle, key -> new State());
			if (state.departureIndex != departureIndex || state.sidingDepartureTime != sidingDepartureTime) {
				state.departureIndex = departureIndex;
				state.sidingDepartureTime = sidingDepartureTime;
				state.delayMs = 0L;
			}
		}
	}

	/**
	 * 清除某列车累计的人为时间损耗。
	 *
	 * @param vehicle 列车对象(可为空)
	 */
	public static void clear(Object vehicle) {
		if (vehicle == null) {
			return;
		}
		synchronized (STATES) {
			STATES.remove(vehicle);
		}
	}
}
