package top.s1metro.s1mtr.item;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import top.s1metro.s1mtr.client.S1mtraddonClient;
import top.s1metro.s1mtr.client.screen.NodeCopierConfigScreen;
import top.s1metro.s1mtr.network.PacketS1mtrCopyNode;
import top.s1metro.s1mtr.network.PacketS1mtrPasteNode;
import top.s1metro.s1mtr.network.PacketS1mtrSaveNodeCopy;

/**
 * 轨道节点复制粘贴工具。
 * <p>
 * <b>贴图 1(默认)</b>:右键一个<b>已连接轨道的轨道节点</b>,读取该节点所有连接
 * (另一端节点坐标 + 节点朝向 + 每条轨道属性),保存到物品 NBT,并切换到贴图 2。
 * <p>
 * <b>贴图 2(已复制)</b>:右键一个空地,像放置方块一样在点击面旁放置新的轨道节点并自动连接。
 * <p>
 * <b>Shift+右键</b>:切换复制模式:
 * <ul>
 *   <li><b>连接模式</b>(默认):粘贴时连接已有的轨道节点。</li>
 *   <li><b>完全复制</b>:粘贴时按相对位置放置一组新的轨道节点(每个相连节点对应一个新节点,
 *       位置 = 粘贴点 + 相对偏移),并连接它们,节点朝向保留。适合快速建设多线铁路。</li>
 * </ul>
 * <p>
 * 贴图切换用物品 damage(核心属性,自动同步,绕开 NBT 同步时序问题)。
 */
public class ItemNodeCopier extends Item {

	public static final String KEY_DATA = "s1mtr_copied";
	public static final String KEY_MODE = "s1mtr_mode";

	/** 连接模式(默认):粘贴时连接已有节点。 */
	public static final int MODE_CONNECT = 0;
	/** 完全复制模式:按相对位置复制整组节点。 */
	public static final int MODE_COPY_ALL = 1;

	public ItemNodeCopier(Settings settings) {
		super(settings.maxCount(1).maxDamage(1));
	}

	/** 隐藏耐久条(damage 仅用于贴图切换)。 */
	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return false;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		final World world = context.getWorld();
		final PlayerEntity player = context.getPlayer();
		final ItemStack stack = context.getStack();
		if (player == null) {
			return ActionResult.PASS;
		}

		if (world.isClient()) {
			// Shift+右键:打开配置界面切换复制模式
			if (player.isSneaking()) {
				MinecraftClient.getInstance().setScreen(
						new NodeCopierConfigScreen(stack, context.getHand() == Hand.OFF_HAND));
				return ActionResult.SUCCESS;
			}
			// 右键轨道节点 -> 读取连接数据并发往服务端保存
			final net.minecraft.block.BlockState state = world.getBlockState(context.getBlockPos());
			if (state.getBlock() instanceof org.mtr.mod.block.BlockNode) {
				final String json = PacketS1mtrCopyNode.collectConnections(context.getBlockPos(), state);
				if (json != null) {
					S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
							new PacketS1mtrSaveNodeCopy(json, context.getHand() == Hand.OFF_HAND));
					// 立即切换贴图(damage 触发模型 overrides;服务端同步后保持)
					setCopiedData(stack, json);
				}
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		}

		// 服务端:右键非节点且非 Shift+右键、且持有数据 -> 像放置方块一样,在点击面的相邻位置放置新节点
		if (player.isSneaking()) {
			return ActionResult.PASS;
		}
		final net.minecraft.block.BlockState state = world.getBlockState(context.getBlockPos());
		if (!(state.getBlock() instanceof org.mtr.mod.block.BlockNode) && hasCopiedData(stack)) {
			if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
				final BlockPos placePos = context.getBlockPos().offset(context.getSide());
				PacketS1mtrPasteNode.handle(serverPlayer, stack, placePos, getMode(stack));
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	// ===== NBT 工具 =====

	public static boolean hasCopiedData(ItemStack stack) {
		return stack != null && stack.getOrCreateNbt().contains(KEY_DATA);
	}

	public static String getCopiedData(ItemStack stack) {
		return stack.getOrCreateNbt().getString(KEY_DATA);
	}

	public static void setCopiedData(ItemStack stack, String json) {
		stack.getOrCreateNbt().putString(KEY_DATA, json);
		// damage=1 触发模型 overrides(damaged:1) 切换到贴图 2;damage 是核心属性,服务端/客户端自动同步
		stack.setDamage(1);
	}

	public static void clearCopiedData(ItemStack stack) {
		stack.getOrCreateNbt().remove(KEY_DATA);
		stack.setDamage(0);
	}

	public static int getMode(ItemStack stack) {
		return stack.getOrCreateNbt().getInt(KEY_MODE);
	}

	public static void setMode(ItemStack stack, int mode) {
		stack.getOrCreateNbt().putInt(KEY_MODE, mode == MODE_COPY_ALL ? MODE_COPY_ALL : MODE_CONNECT);
	}
}
