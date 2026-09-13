package top.s1metro.s1mtr.client;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * 主源码集到客户端代码的反射桥梁。
 * <p>
 * 主源码集里的类（如 Item、网络包）会在服务端也被加载，因此绝对不能直接
 * 引用 client.screen / client 包下继承 {@code Screen} 的类，否则服务端启动会因
 * 缺少客户端类而崩溃（NoClassDefFoundError: net/minecraft/class_437）。
 * <p>
 * 本类把所有"需要触达客户端类"的操作收敛到反射调用，由它去加载真正的
 * 客户端类 {@link ClientScreenOpener}（只在客户端被加载）。服务端永远不会执行到
 * 反射调用内部，因此不会链到任何客户端类。
 */
public final class S1mtrClientProxy {

	private static final String OPENER_CLASS = "top.s1metro.s1mtr.client.ClientScreenOpener";

	private S1mtrClientProxy() {
	}

	/** 通过无参静态方法打开对应界面。methodName 为 ClientScreenOpener 中的方法名。 */
	public static void openScreen(String methodName) {
		try {
			final Class<?> opener = Class.forName(OPENER_CLASS);
			opener.getMethod(methodName).invoke(null);
		} catch (Throwable ignored) {
			// 仅在客户端调用，且客户端必然存在这些类；异常仅作防御
		}
	}

	/** 打开快速建造配置界面（需要 ItemStack 参数）。 */
	public static void openFastTrackConfig(ItemStack stack) {
		try {
			final Class<?> opener = Class.forName(OPENER_CLASS);
			opener.getMethod("openFastTrackConfig", ItemStack.class).invoke(null, stack);
		} catch (Throwable ignored) {
		}
	}

	/** 打开自动速度连接器配置界面（需要 ItemStack 参数）。 */
	public static void openAutoConnectorConfig(ItemStack stack) {
		try {
			final Class<?> opener = Class.forName(OPENER_CLASS);
			opener.getMethod("openAutoConnectorConfig", ItemStack.class).invoke(null, stack);
		} catch (Throwable ignored) {
		}
	}

	/** 打开节点复制器配置界面（需要 ItemStack + 是否副手 参数）。 */
	public static void openNodeCopierConfig(ItemStack stack, boolean offHand) {
		try {
			final Class<?> opener = Class.forName(OPENER_CLASS);
			opener.getMethod("openNodeCopierConfig", ItemStack.class, boolean.class).invoke(null, stack, offHand);
		} catch (Throwable ignored) {
		}
	}

	/** 客户端复制轨道节点连接数据（收集 + 发服务端 + 本地切换贴图）。 */
	public static void copyNodeConnections(ItemStack stack, BlockPos pos, BlockState state, boolean offHand) {
		try {
			final Class<?> opener = Class.forName(OPENER_CLASS);
			opener.getMethod("copyNodeConnections", ItemStack.class, BlockPos.class, BlockState.class, boolean.class)
					.invoke(null, stack, pos, state, offHand);
		} catch (Throwable ignored) {
		}
	}

	/** 当前客户端是否打开了任意界面。 */
	public static boolean isScreenOpen() {
		try {
			final Class<?> opener = Class.forName(OPENER_CLASS);
			return (boolean) opener.getMethod("isScreenOpen").invoke(null);
		} catch (Throwable ignored) {
			return false;
		}
	}
}