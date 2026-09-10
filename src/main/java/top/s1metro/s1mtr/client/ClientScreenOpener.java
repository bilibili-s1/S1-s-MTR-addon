package top.s1metro.s1mtr.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import top.s1metro.s1mtr.client.screen.AutoConnectorConfigScreen;
import top.s1metro.s1mtr.client.screen.FastTrackConfigScreen;
import top.s1metro.s1mtr.client.screen.NodeCopierConfigScreen;
import top.s1metro.s1mtr.client.screen.RailNetworkMapScreen;
import top.s1metro.s1mtr.client.screen.StationListScreen;
import top.s1metro.s1mtr.item.ItemNodeCopier;
import top.s1metro.s1mtr.network.PacketS1mtrCopyNode;
import top.s1metro.s1mtr.network.PacketS1mtrSaveNodeCopy;

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

	public static void openAutoConnectorConfig(ItemStack stack) {
		final MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(new AutoConnectorConfigScreen(stack));
	}

	public static void openNodeCopierConfig(ItemStack stack, boolean offHand) {
		final MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(new NodeCopierConfigScreen(stack, offHand));
	}

	/**
	 * 客户端执行"复制":读取被右键轨道节点的连接数据并发往服务端，成功后本地切换到贴图 2。
	 * 仅客户端调用（由 {@code S1mtrClientProxy} 反射进入）。
	 */
	public static void copyNodeConnections(ItemStack stack, net.minecraft.util.math.BlockPos pos,
			net.minecraft.block.BlockState state, boolean offHand) {
		final String json = PacketS1mtrCopyNode.collectConnections(pos, state);
		if (json != null) {
			S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(new PacketS1mtrSaveNodeCopy(json, offHand));
			ItemNodeCopier.setCopiedData(stack, json);
		}
	}

	/** 当前是否已打开某个界面（用于在客户端判断是否应拦截右键等逻辑）。 */
	public static boolean isScreenOpen() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client.currentScreen != null;
	}
}
