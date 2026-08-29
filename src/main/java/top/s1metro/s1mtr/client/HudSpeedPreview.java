package top.s1metro.s1mtr.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import top.s1metro.s1mtr.item.ItemRailConnectorAuto;

/**
 * 自动速度轨道连接器的 HUD 预览辅助。
 * <p>
 * 当手持该连接器且已选第一点时，在快捷栏上方显示"已选点 → 当前瞄准点"的预期限速(km/h)。
 * <ul>
 *   <li>瞄准轨道节点 → 计算到该节点的建议限速（反映真实朝向与弯道）；</li>
 *   <li>瞄准普通方块 → 计算到其上一格的建议限速（按直线/坡度估算）。</li>
 * </ul>
 * 预览线由 MTR 的 {@code ItemNodeModifierBase} 客户端逻辑自动绘制，此处仅补充限速文字。
 */
public final class HudSpeedPreview {

	private HudSpeedPreview() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register(HudSpeedPreview::renderHud);
	}

	private static void renderHud(DrawContext drawContext, float tickDelta) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final ClientPlayerEntity player = client.player;
		if (player == null || client.crosshairTarget == null) {
			return;
		}
		final net.minecraft.item.ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof ItemRailConnectorAuto)) {
			return;
		}
		// 第一点由 MTR 的 ItemBlockClickingBase 存入物品 NBT 的 "pos" (long) 字段
		final net.minecraft.nbt.NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains("pos")) {
			return;
		}
		final BlockPos first = BlockPos.fromLong(nbt.getLong("pos"));
		if (first == null || client.world == null) {
			return;
		}

		final BlockPos second = resolveSecondPos(client.world, client.crosshairTarget);
		final long previewSpeed;
		if (second != null) {
			previewSpeed = RailSpeedHelper.recommendForPreview(
					new org.mtr.core.data.Position(first.getX(), first.getY(), first.getZ()),
					second, client.world);
		} else {
			previewSpeed = -1;
		}

		// 在快捷栏上方居中显示预期速度
		final int width = drawContext.getScaledWindowWidth();
		final int height = drawContext.getScaledWindowHeight();
		final int y = height - 70; // 快捷栏(约 height-22)上方
		final Text text;
		if (previewSpeed < 0) {
			text = Text.translatable("gui.s1mtr.auto_connector.preview_unknown");
		} else {
			text = Text.translatable("gui.s1mtr.auto_connector.preview_speed", previewSpeed);
		}
		final int textWidth = client.textRenderer.getWidth(text);
		drawContext.drawTextWithShadow(client.textRenderer, text, (width - textWidth) / 2, y, 0x55FFAA);
	}

	/**
	 * 确定"候选第二点"：瞄准轨道节点就用它；否则用其上方一格。
	 */
	private static BlockPos resolveSecondPos(ClientWorld world, net.minecraft.util.hit.HitResult hit) {
		if (!(hit instanceof BlockHitResult blockHit)) {
			return null;
		}
		final BlockPos pos = blockHit.getBlockPos();
		final net.minecraft.block.BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof org.mtr.mod.block.BlockNode) {
			return pos;
		}
		return pos.up();
	}
}
