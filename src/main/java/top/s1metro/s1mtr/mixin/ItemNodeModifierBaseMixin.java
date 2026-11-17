package top.s1metro.s1mtr.mixin;

import org.mtr.core.data.Position;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.TwoPositionsBase;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemUsageContext;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.Init;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemRailModifier;
import org.mtr.mod.packet.PacketDeleteData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlockEntity;
import top.s1metro.s1mtr.service.NodeConnector;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 让 MTR 轨道连接器识别并连接万向节点（最小可用版）。
 * <p>
 * 覆盖三个点：
 * <ul>
 *   <li>{@code clickCondition} — 允许轨道连接器以万向节点为连接起点/终点</li>
 *   <li>{@code onStartClick} — 万向节点作为起点时写入 transportMode，避开原版 BlockNode cast</li>
 *   <li>{@code onEndClick} — 涉及万向节点时按绑定状态计算角度并建轨 / 删轨</li>
 * </ul>
 * 角度候选按方速的策略降级：两端均未绑定取直线，一端绑定取最大半径圆弧切向，
 * 都绑定则用既有角度，几何不成立时退回直线。方向由 {@link AngleExtra} 生成任意精度 Angle。
 *
 * <p>移植自 FangSu For MTR 4（MIT），按 Yarn 映射重写，砍掉信号/桥梁反射分支与节点姿态逻辑。
 */
@Mixin(value = org.mtr.mod.item.ItemNodeModifierBase.class, remap = false)
public abstract class ItemNodeModifierBaseMixin {

	/** 注意：不能声明 final（final + 初始值会被常量折叠），也不能带初始值。 */
	@Shadow(remap = false)
	protected boolean isConnector;

	@Inject(method = "clickCondition", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$clickCondition(ItemUsageContext context, CallbackInfoReturnable<Boolean> cir) {
		final World world = context.getWorld().data;
		if (world != null && isMultiDirectionNode(world, context.getBlockPos().data)
				&& context.getStack().data.getItem() instanceof ItemRailModifier) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "onStartClick", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$onStartClick(ItemUsageContext context, CompoundTag compoundTag, CallbackInfo ci) {
		final World world = context.getWorld().data;
		if (world != null && isMultiDirectionNode(world, context.getBlockPos().data)) {
			compoundTag.putString("transport_mode", TransportMode.TRAIN.toString());
			ci.cancel();
		}
	}

	@Inject(method = "onEndClick", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$onEndClick(ItemUsageContext context, org.mtr.mapping.holder.BlockPos posEnd,
								   CompoundTag compoundTag, CallbackInfo ci) {
		final World world = context.getWorld().data;
		if (world == null || world.isClient()) {
			return;
		}
		final org.mtr.mapping.holder.PlayerEntity player = context.getPlayer();
		if (player == null || !org.mtr.mapping.holder.ServerPlayerEntity.isInstance(player)) {
			return;
		}

		final BlockPos posStart = context.getBlockPos().data;
		final BlockPos endPos = posEnd.data;
		if (posStart.equals(endPos)) {
			return;
		}
		if (!isMultiDirectionNode(world, posStart) && !isMultiDirectionNode(world, endPos)) {
			return; // 原版逻辑
		}
		final Item heldItem = context.getStack().data.getItem();
		if (!(heldItem instanceof ItemRailModifier)) {
			return;
		}

		final net.minecraft.server.network.ServerPlayerEntity serverPlayer =
				(net.minecraft.server.network.ServerPlayerEntity) player.data;
		final ServerWorld serverWorld = new ServerWorld((net.minecraft.server.world.ServerWorld) world);

		if (!isConnector) {
			// 轨道删除器：删除两端间轨道（connected 复位由 BlockNodeMixin 处理）
			final Position p1 = Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(posStart));
			final Position p2 = Init.blockPosToPosition(new org.mtr.mapping.holder.BlockPos(endPos));
			PacketDeleteData.sendDirectlyToServerRailId(serverWorld, TwoPositionsBase.getHexId(p1, p2));
			ci.cancel();
			return;
		}

		handleRailConnect(world, serverWorld, posStart, endPos, serverPlayer, heldItem);
		ci.cancel();
	}

	private void handleRailConnect(World world, ServerWorld serverWorld, BlockPos posStart, BlockPos endPos,
								  net.minecraft.server.network.ServerPlayerEntity serverPlayer, Item heldItem) {
		final boolean startIsNode = isMultiDirectionNode(world, posStart);
		final boolean endIsNode = isMultiDirectionNode(world, endPos);
		final boolean startBonded = isBonded(world, posStart);
		final boolean endBonded = isBonded(world, endPos);
		final double startAngle = NodeConnector.getDirectionDegrees(world, posStart);
		final double endAngle = NodeConnector.getDirectionDegrees(world, endPos);
		final double straight = NodeConnector.straightAngle(posStart, endPos);

		final List<double[]> candidates = new ArrayList<>();
		if (!startBonded && !endBonded) {
			candidates.add(new double[]{straight, straight});
		} else if (!startBonded) {
			candidates.add(new double[]{NodeConnector.maxRadiusTangentAngle(endPos, endAngle, posStart), endAngle});
			candidates.add(new double[]{straight, straight});
		} else if (!endBonded) {
			candidates.add(new double[]{startAngle, NodeConnector.maxRadiusTangentAngle(posStart, startAngle, endPos)});
			candidates.add(new double[]{straight, straight});
		} else {
			candidates.add(new double[]{startAngle, endAngle});
			if (!endIsNode) {
				candidates.add(new double[]{startAngle, NodeConnector.maxRadiusTangentAngle(posStart, startAngle, endPos)});
			}
			if (!startIsNode || !endIsNode) {
				candidates.add(new double[]{straight, straight});
			}
		}

		final ItemRailModifierAccessorMixin accessor = (ItemRailModifierAccessorMixin) (Object) heldItem;
		final RailType railType = accessor.getRailType();
		final boolean oneWay = accessor.isOneWay();

		for (final double[] candidate : candidates) {
			if (NodeConnector.createAndSendRail(serverWorld, posStart, candidate[0], endPos, candidate[1],
					railType, oneWay, serverPlayer.getUuid())) {
				if (startIsNode && !startBonded) {
					bindNode(world, posStart, candidate[0]);
				}
				if (endIsNode && !endBonded) {
					bindNode(world, endPos, candidate[1]);
				}
				NodeConnector.markConnected(world, posStart);
				NodeConnector.markConnected(world, endPos);
				return;
			}
		}
		serverPlayer.sendMessage(Text.literal("\u00A7c[S1MTR] \u65b9\u5411\u65e0\u6548\uff0c\u65e0\u6cd5\u8fde\u63a5 / Invalid orientation"), false);
	}

	private static boolean isMultiDirectionNode(World world, BlockPos pos) {
		return NodeConnector.isMultiDirectionNode(world, pos);
	}

	private static boolean isBonded(World world, BlockPos pos) {
		final BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof MultiDirectionNodeBlockEntity node) {
			return node.isDirectionBonded();
		}
		// 普通 MTR 节点恒为「已绑定」：blockstate 角度即其绑定方向
		return true;
	}

	private static void bindNode(World world, BlockPos pos, double angle) {
		if (world.getBlockEntity(pos) instanceof MultiDirectionNodeBlockEntity node) {
			node.setDirectionAndBind(angle);
		}
	}
}
