package top.s1metro.s1mtr.mixin;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.block.BlockNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlockEntity;

import net.minecraft.block.entity.BlockEntity;

/**
 * 让 MTR 删除轨道后的 {@code resetRailNode} 也能复位万向节点的 {@code connected} 状态。
 * <p>移植自 FangSu For MTR 4（MIT），按 Yarn 映射重写。
 */
@Mixin(value = BlockNode.class, remap = false)
public class BlockNodeMixin {

	@Inject(method = "resetRailNode", at = @At("TAIL"), remap = false)
	private static void s1mtr$resetMultiDirectionNode(ServerWorld serverWorld, BlockPos blockPos, CallbackInfo ci) {
		final net.minecraft.server.world.ServerWorld world = serverWorld.data;
		final net.minecraft.util.math.BlockPos pos = blockPos.data;
		if (world == null || pos == null) {
			return;
		}
		final BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof MultiDirectionNodeBlockEntity node) {
			node.setConnected(false);
		}
	}
}
