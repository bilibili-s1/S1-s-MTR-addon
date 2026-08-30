package top.s1metro.s1mtr.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import top.s1metro.s1mtr.client.S1mtraddonClient;
import top.s1metro.s1mtr.network.PacketS1mtrCopyNode;
import top.s1metro.s1mtr.network.PacketS1mtrPasteNode;
import top.s1metro.s1mtr.network.PacketS1mtrSaveNodeCopy;

/**
 * 轨道节点复制粘贴工具。
 * <p>
 * <b>贴图 1(默认)</b>:右键一个<b>已连接轨道的轨道节点</b>,读取该节点所有连接
 * (另一端节点坐标 + 每条轨道的属性:限速/样式/形状/单向等),保存到物品 NBT,并切换到贴图 2。
 * <p>
 * <b>贴图 2(已复制)</b>:右键一个空地,放置一个 MTR 轨道节点方块,并用保存的属性
 * 自动把新节点连接到原节点。随后清除数据,切回贴图 1。
 * <p>
 * 可用于方便地移动轨道节点位置(在原节点旁放置新节点并自动重连)。
 */
public class ItemNodeCopier extends Item {

	public static final String KEY_DATA = "s1mtr_copied";

	public ItemNodeCopier(Settings settings) {
		super(settings.maxCount(1));
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
			// 客户端:右键轨道节点 -> 读取连接数据并发往服务端保存
			final net.minecraft.block.BlockState state = world.getBlockState(context.getBlockPos());
			final boolean isNode = state.getBlock() instanceof org.mtr.mod.block.BlockNode;
			if (isNode) {
				final String json = PacketS1mtrCopyNode.collectConnections(context.getBlockPos());
				if (json != null) {
					S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
							new PacketS1mtrSaveNodeCopy(json));
					// 立即切换贴图(custom_model_data 触发模型 overrides;服务端回执后也保持)
					setCopiedData(stack, json);
				}
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		}

		// 服务端:右键且持有数据 -> 像放置方块一样,在点击面的相邻位置放置新节点并自动连接
		final net.minecraft.block.BlockState state = world.getBlockState(context.getBlockPos());
		if (!(state.getBlock() instanceof org.mtr.mod.block.BlockNode) && hasCopiedData(stack)) {
			if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
				// 放置位置 = 点击方块 + 点击面方向(不替换原方块,与放置方块逻辑一致)
				final net.minecraft.util.math.BlockPos placePos =
						context.getBlockPos().offset(context.getSide());
				PacketS1mtrPasteNode.handle(serverPlayer, stack, placePos);
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
		// 原版 custom_model_data 触发模型 overrides 切换到贴图 2
		stack.getOrCreateNbt().putInt("custom_model_data", 1);
	}

	public static void clearCopiedData(ItemStack stack) {
		stack.getOrCreateNbt().remove(KEY_DATA);
		stack.getOrCreateNbt().putInt("custom_model_data", 0);
	}
}
