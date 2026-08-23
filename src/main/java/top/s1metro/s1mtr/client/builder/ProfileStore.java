package top.s1metro.s1mtr.client.builder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;

/**
 * 剖面预制存档管理。
 * <p>
 * 将分层调度表保存为 JSON 文件,存放于 {@code <游戏目录>/config/s1mtr/profiles/} 目录,
 * 文件内容为 {@code {"name": "...", "data": "<CompositeLayerSchedule.serialize() 结果>"}}。
 * 预制剖面可在 {@link top.s1metro.s1mtr.client.screen.PresetManagerScreen} 中查看与取用。
 * <p>
 * 兼容:旧版单剖面格式的预制文件(数据段为 CompositeProfile 序列化结果)会被
 * {@link CompositeLayerSchedule#deserialize(String)} 自动识别并转为单层调度表。
 */
public final class ProfileStore {

	private static final String DIR_NAME = "s1mtr";
	private static final String PROFILES_DIR_NAME = "profiles";
	private static final String FILE_EXTENSION = ".json";

	private ProfileStore() {
	}

	/** 获取预制剖面存放目录(不存在则创建)。 */
	public static File getProfilesDir() {
		final File runDir = MinecraftClient.getInstance().runDirectory;
		final File dir = new File(new File(new File(runDir, "config"), DIR_NAME), PROFILES_DIR_NAME);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}

	/** 列出所有已保存的预制剖面文件(按文件名排序)。 */
	public static List<File> listPresets() {
		final File[] files = getProfilesDir().listFiles((dir, name) -> name.endsWith(FILE_EXTENSION));
		final List<File> result = new ArrayList<>();
		if (files != null) {
			java.util.Arrays.sort(files, Comparator.comparing(File::getName));
			for (final File file : files) {
				result.add(file);
			}
		}
		return result;
	}

	/** 获取预制剖面的显示名(文件名去掉扩展名)。 */
	public static String getPresetName(File file) {
		final String name = file.getName();
		return name.endsWith(FILE_EXTENSION) ? name.substring(0, name.length() - FILE_EXTENSION.length()) : name;
	}

	/** 清洗名字中 Windows 不允许出现在文件名里的字符。 */
	public static String sanitizeName(String name) {
		if (name == null) {
			return "";
		}
		return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
	}

	/** 将分层调度表保存为预制文件,成功返回 true。 */
	public static boolean savePreset(String name, CompositeLayerSchedule schedule) {
		final String safeName = sanitizeName(name);
		if (safeName.isEmpty() || schedule == null) {
			return false;
		}
		try {
			final JsonObject json = new JsonObject();
			json.addProperty("name", safeName);
			json.addProperty("data", schedule.serialize());
			final File file = new File(getProfilesDir(), safeName + FILE_EXTENSION);
			Files.writeString(file.toPath(), json.toString(), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/** 保存单层剖面(向后兼容入口,内部转为单层 schedule)。 */
	public static boolean savePreset(String name, CompositeProfile profile) {
		final CompositeLayerSchedule schedule = new CompositeLayerSchedule();
		if (profile != null) {
			schedule.entries().get(0).profile = profile;
			schedule.entries().get(0).length = 1;
		}
		return savePreset(name, schedule);
	}

	/** 从预制文件加载分层调度表,失败返回 null。 */
	public static CompositeLayerSchedule loadPreset(File file) {
		try {
			final String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
			final JsonObject json = JsonParser.parseString(content).getAsJsonObject();
			final String data = json.has("data") ? json.get("data").getAsString() : "";
			return CompositeLayerSchedule.deserialize(data);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
