package top.s1metro.s1mtr.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import top.s1metro.s1mtr.client.screen.FastTrackConfigScreen;
import top.s1metro.s1mtr.client.screen.RailNetworkMapScreen;
import top.s1metro.s1mtr.client.screen.StationListScreen;

/**
 * 客户端界面打开代理。
 * <p>
 * 这个类只在客户端被加载（由 Item 通过反射调用）。把它单独抽出来，
 * 是为了避免主源码集里的 Item 类在编译期/链接期直接依赖 client.screen 包下的
 * {@code Screen} 子类，否则服务端启动时（无客户端类）会抛出 NoClassDefFoundError。
 */
public final class ClientScreenOpener {

	private ClientScreenOpener() {
	}

	public static void openStationList() {
		final MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(new StationListScreen());
	}

	public static void openRailNetworkMap() {
		final MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(new RailNetworkMapScreen());
	}

	public static void openFastTrackConfig(ItemStack stack) {
		final MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(new FastTrackConfigScreen(stack));
	}

	/** 当前是否已打开某个界面（用于在客户端判断是否应拦截右键等逻辑）。 */
	public static boolean isScreenOpen() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client.currentScreen != null;
	}
}
