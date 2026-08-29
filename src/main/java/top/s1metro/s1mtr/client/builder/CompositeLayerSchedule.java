package top.s1metro.s1mtr.client.builder;

import java.util.ArrayList;
import java.util.List;

/**
 * 分层放样调度表:管理多个 {@link CompositeProfile} 剖面及各自持续长度。
 * <p>
 * 放样时按列表顺序循环切换剖面:每段剖面持续 N 格后切到下一段,列表末尾后回到第一段。
 * 例如两个剖面 A(长度 5) 与 B(长度 1),放样顺序为 AAAAA B AAAAA B ... 直到轨道结束。
 * <p>
 * 序列化格式:用 {@code ;} 分隔条目,每条目为 {@code <length>:<profile.serialize()>}。
 * 条目内的 {@code ;} 不存在(profile 用 {@code |} 分隔),所以无需额外转义。
 * 空调度表序列化为空串。旧格式(无 length 前缀)在反序列化时按单剖面、length=∞ 处理。
 */
public final class CompositeLayerSchedule {

	public static final int MAX_LAYERS = 10;
	/** length=0 表示该剖面一直放样到轨道结束(只用于单层场景)。 */
	public static final int LENGTH_INFINITE = 0;

	private final List<Entry> entries = new ArrayList<>();

	public static final class Entry {
		public CompositeProfile profile;
		public int length;

		public Entry(CompositeProfile profile, int length) {
			this.profile = profile;
			this.length = length;
		}
	}

	public CompositeLayerSchedule() {
		entries.add(new Entry(new CompositeProfile(), 1));
	}

	public int size() {
		return entries.size();
	}

	public Entry get(int index) {
		return entries.get(index);
	}

	public List<Entry> entries() {
		return entries;
	}

	/** 添加一个新的默认剖面,返回新剖面索引;已达上限返回 -1。 */
	public int addLayer() {
		if (entries.size() >= MAX_LAYERS) {
			return -1;
		}
		entries.add(new Entry(new CompositeProfile(), 1));
		return entries.size() - 1;
	}

	/** 添加指定剖面(深拷贝)到末尾,返回新剖面索引;已达上限返回 -1。 */
	public int addLayer(CompositeProfile profile) {
		if (entries.size() >= MAX_LAYERS || profile == null) {
			return -1;
		}
		entries.add(new Entry(profile.copy(), 1));
		return entries.size() - 1;
	}

	/** 删除指定索引的剖面;至少保留一个剖面。 */
	public void removeLayer(int index) {
		if (entries.size() <= 1 || index < 0 || index >= entries.size()) {
			return;
		}
		entries.remove(index);
	}

	/** 交换两个剖面顺序。 */
	public void swap(int i, int j) {
		if (i < 0 || j < 0 || i >= entries.size() || j >= entries.size()) {
			return;
		}
		final Entry tmp = entries.get(i);
		entries.set(i, entries.get(j));
		entries.set(j, tmp);
	}

	/** 设置某剖面的长度(格数),length=0 表示无限(仅单层时有效)。 */
	public void setLength(int index, int length) {
		if (index < 0 || index >= entries.size()) {
			return;
		}
		entries.get(index).length = Math.max(0, length);
	}

	/** 设置某剖面的数据。 */
	public void setProfile(int index, CompositeProfile profile) {
		if (index < 0 || index >= entries.size() || profile == null) {
			return;
		}
		entries.get(index).profile = profile;
	}

	/**
	 * 根据轨道纵向距离返回对应的剖面索引。
	 * <p>
	 * 单层(length=0)时直接返回 0。多层时累加各段长度,循环切换。
	 */
	public int getLayerIndexAt(double distance) {
		if (entries.isEmpty()) {
			return -1;
		}
		if (entries.size() == 1) {
			return 0;
		}
		int totalCycle = 0;
		for (Entry e : entries) {
			if (e.length > 0) {
				totalCycle += e.length;
			}
		}
		if (totalCycle <= 0) {
			return 0;
		}
		int pos = (int) Math.floor(distance) % totalCycle;
		if (pos < 0) {
			pos += totalCycle;
		}
		int acc = 0;
		for (int i = 0; i < entries.size(); i++) {
			acc += entries.get(i).length;
			if (pos < acc) {
				return i;
			}
		}
		return entries.size() - 1;
	}

	/** 序列化为单行字符串。 */
	public String serialize() {
		if (entries.isEmpty()) {
			return "";
		}
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < entries.size(); i++) {
			if (i > 0) {
				sb.append(';');
			}
			final Entry e = entries.get(i);
			sb.append(e.length).append(':').append(e.profile.serialize());
		}
		return sb.toString();
	}

	/** 反序列化。旧格式(单个 profile 数据,无 length: 前缀)按单层无限长度处理。 */
	public static CompositeLayerSchedule deserialize(String data) {
		final CompositeLayerSchedule schedule = new CompositeLayerSchedule();
		schedule.entries.clear();
		if (data == null || data.isEmpty()) {
			schedule.entries.add(new Entry(new CompositeProfile(), LENGTH_INFINITE));
			return schedule;
		}
		// 判断是否是新格式(含 ; 或以 <数字>: 开头)
		final boolean isNewFormat = data.indexOf(';') >= 0
				|| (data.length() > 0 && Character.isDigit(data.charAt(0)) && data.indexOf(':') >= 0
				&& isLengthPrefix(data));
		if (isNewFormat) {
			final String[] parts = data.split(";", -1);
			for (String part : parts) {
				if (part.isEmpty()) {
					continue;
				}
				final int colon = part.indexOf(':');
				if (colon < 0) {
					continue;
				}
				try {
					final int length = Integer.parseInt(part.substring(0, colon));
					final String profileData = part.substring(colon + 1);
					schedule.entries.add(new Entry(CompositeProfile.deserialize(profileData), length));
				} catch (NumberFormatException ignored) {
				}
			}
		} else {
			// 旧格式:单个 profile 数据,按单层无限长度
			schedule.entries.add(new Entry(CompositeProfile.deserialize(data), LENGTH_INFINITE));
		}
		if (schedule.entries.isEmpty()) {
			schedule.entries.add(new Entry(new CompositeProfile(), LENGTH_INFINITE));
		}
		return schedule;
	}

	/** 判断 data 开头是否是 {@code <数字>:} 形式的长度前缀。 */
	private static boolean isLengthPrefix(String data) {
		final int colon = data.indexOf(':');
		if (colon <= 0 || colon >= data.length() - 1) {
			return false;
		}
		for (int i = 0; i < colon; i++) {
			if (!Character.isDigit(data.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public CompositeLayerSchedule copy() {
		final CompositeLayerSchedule copy = new CompositeLayerSchedule();
		copy.entries.clear();
		for (Entry e : entries) {
			copy.entries.add(new Entry(e.profile.copy(), e.length));
		}
		return copy;
	}

	public void copyFrom(CompositeLayerSchedule other) {
		if (other == null) {
			return;
		}
		entries.clear();
		for (Entry e : other.entries) {
			entries.add(new Entry(e.profile.copy(), e.length));
		}
	}
}
