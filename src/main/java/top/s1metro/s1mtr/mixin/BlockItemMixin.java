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
import top.s1metro.s1mtr.item.ItemFastTrackBuilder;
import top.s1metro.s1mtr.service.FastTrackConnectionHelper;

/**
 * 监听玩家放置方块。当放置的是轨道节点方块 (BlockNode) 且副手持有快速建造轨道工具时，
 * 自动从上一个节点连接轨道并进行复合放样。
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

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
		if (!(context.getWorld().getBlockState(context.getBlockPos()).getBlock() instanceof BlockNode)) {
			return;
		}
		final ItemStack offHand = player.getOffHandStack();
		if (!(offHand.getItem() instanceof ItemFastTrackBuilder)) {
			return;
		}
		FastTrackConnectionHelper.onNodePlaced(player, offHand, context.getBlockPos());
	}
}
