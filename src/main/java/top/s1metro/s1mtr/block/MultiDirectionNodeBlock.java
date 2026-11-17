package top.s1metro.s1mtr.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.mtr.core.data.Position;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.Items;
import org.mtr.mod.packet.PacketDeleteData;

/**
 * 万向节点方块（移植自 FangSu For MTR 4，MIT，按 Yarn 映射重写为最小可用版）。
 * <p>
 * 与 MTR 原版轨道节点的区别：未绑定方向时标记持续旋转，可用刷子右键在配置界面指定任意角度并绑定，
 * 也可直接用轨道连接器连接两个未绑定节点（自动取直线方向 / 最大半径圆弧切向）。
 * 任意角度由 {@code AngleExtra} 的幻影 Angle 实例实现，不受原版 22.5° 离散限制。
 * <p>
 * 最小可用版不含节点平移、俯仰/翻滚、外轨超高与自定义 RailMath 几何内核。
 */
public class MultiDirectionNodeBlock extends Block implements BlockEntityProvider {

	public MultiDirectionNodeBlock() {
		super(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque());
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new MultiDirectionNodeBlockEntity(pos, state);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		final BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof MultiDirectionNodeBlockEntity node && node.isConnected()) {
			// 已连接时极薄，与原版 MTR 节点一致，避免遮挡玩家视线追踪轨道
			return VoxelShapes.cuboid(0.1, 0, 0.1, 0.9, 0.0625, 0.9);
		}
		return VoxelShapes.fullCube();
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		// 与原版 MTR 节点一致：不参与碰撞
		return VoxelShapes.empty();
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		final ItemStack stack = player.getStackInHand(hand);
		// 刷子右键：打开方向/绑定配置界面（客户端）
		if (stack.getItem() == Items.BRUSH.get().data) {
			if (world.isClient()) {
				top.s1metro.s1mtr.client.S1mtrClientProxy.openMultiDirectionNodeConfig(pos);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	/** 破坏方块时删除连接到此节点的所有轨道（与 MTR 原版 BlockNode.onBreak 行为一致）。 */
	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!world.isClient() && !moved && !state.isOf(newState.getBlock())) {
			if (world instanceof net.minecraft.server.world.ServerWorld serverLevel) {
				final ServerWorld serverWorld = new ServerWorld(serverLevel);
				PacketDeleteData.sendDirectlyToServerRailNodePosition(
						serverWorld, new Position(pos.getX(), pos.getY(), pos.getZ()));
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}
}
