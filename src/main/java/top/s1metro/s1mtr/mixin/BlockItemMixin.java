package top.s1metro.s1mtr.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.mtr.mod.block.BlockNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlock;
import top.s1metro.s1mtr.item.ItemFastTrackBuilder;
import top.s1metro.s1mtr.service.FastTrackConnectionHelper;

/**
 * 监听玩家放置方块。当放置的是轨道节点方块 (BlockNode) 且副手持有快速建造轨道工具时，
 * 自动从上一个节点连接轨道并进行复合放样。
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

	/**
	 * 副手持快速轨道建设器、主手持原版轨道节点时，自动把要放置的节点替换为万向节点。
	 * 无方速时用 S1MTR 的万向节点；装方速时不替换（方速万向节点由它自己管理）。
	 */
	@Inject(method = "place", at = @At("HEAD"))
	private void s1mtr$swapNodeForFastBuilder(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
		if (top.s1metro.s1mtr.mixin.S1mtrMixinPlugin.isFangsuLoaded()) {
			return;
		}
		final net.minecraft.entity.player.PlayerEntity player = context.getPlayer();
		if (player == null || !(player.getOffHandStack().getItem() instanceof ItemFastTrackBuilder)) {
			return;
		}
		final ItemStack main = context.getStack();
		if (main.isEmpty() || !(main.getItem() instanceof BlockItem mainBlockItem)
				|| !(mainBlockItem.getBlock() instanceof BlockNode)) {
			return;
		}
		final BlockItem replacement = top.s1metro.s1mtr.S1mtraddon.MULTI_DIRECTION_NODE_ITEM;
		if (replacement == null) {
			return;
		}
		try {
			java.lang.reflect.Field stackField = null;
			Class<?> clazz = context.getClass();
			while (clazz != null && stackField == null) {
				for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
					if (f.getType() == ItemStack.class) {
						stackField = f;
						break;
					}
				}
				clazz = clazz.getSuperclass();
			}
			if (stackField != null) {
				stackField.setAccessible(true);
				stackField.set(context, new ItemStack(replacement));
			}
		} catch (Exception ignored) {
		}
	}

	@Inject(method = "place", at = @At("RETURN"))
	private void s1mtr$onNodeBlockPlaced(ItemPlacementContext context,
										 CallbackInfoReturnable<ActionResult> cir) {
		if (context.getWorld().isClient()) {
			return;
		}
		final ActionResult result = cir.getReturnValue();
		if (result != ActionResult.SUCCESS && result != ActionResult.CONSUME) {
			return;
		}
		if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
			return;
		}
		final net.minecraft.block.Block placed = context.getWorld().getBlockState(context.getBlockPos()).getBlock();
		if (!(placed instanceof BlockNode) && !(placed instanceof MultiDirectionNodeBlock)
				&& !isFangsuMultiDirectionNode(placed)) {
			return;
		}
		final ItemStack offHand = player.getOffHandStack();
		if (!(offHand.getItem() instanceof ItemFastTrackBuilder)) {
			return;
		}
		FastTrackConnectionHelper.onNodePlaced(player, offHand, context.getBlockPos());
	}

	/** 方速万向节点（不硬依赖方速类，兼容装/不装方速）。 */
	private static boolean isFangsuMultiDirectionNode(net.minecraft.block.Block block) {
		return block != null && "com.fangsu.blocks.BlockMultiDirectionNode".equals(block.getClass().getName());
	}
}
