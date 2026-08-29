package top.s1metro.s1mtr.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.mtr.core.data.Rail;
import top.s1metro.s1mtr.client.builder.CompositeBuilder;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;

import java.util.List;

/**
 * 复合构建器的 3D 世界内方块预览。
 * <p>
 * 当 {@code CompositeProfileEditorScreen} 打开且持有目标轨道时,把当前分层调度表经
 * {@link CompositeBuilder#collectBlocks} 映射到世界坐标,在世界渲染阶段以半透明模型叠加显示,
 * 实时反映方块位置、朝向与样式。编辑器关闭或数据清空后自动消失。
 * <p>
 * 预览数据在 {@link #setPreview} 时一次性计算并缓存,渲染阶段只读缓存,避免每帧重算卡顿;
 * 网格/剖面/方块样式变化时由编辑器重新调用 {@link #setPreview} 刷新。
 */
public final class CompositePreviewRenderer {

	private static Rail previewRail;
	private static List<CompositeBuilder.PreviewBlock> previewBlocks = java.util.Collections.emptyList();
	private static boolean registered = false;

	private CompositePreviewRenderer() {
	}

	/** 注册世界渲染回调(幂等,只注册一次)。在客户端初始化时调用。 */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WorldRenderEvents.AFTER_TRANSLUCENT.register(CompositePreviewRenderer::render);
	}

	/** 设置当前预览的轨道与调度表(立即计算并缓存方块列表)。 */
	public static void setPreview(Rail rail, CompositeLayerSchedule schedule) {
		previewRail = rail;
		if (rail == null || schedule == null) {
			previewBlocks = java.util.Collections.emptyList();
		} else {
			previewBlocks = CompositeBuilder.collectBlocks(rail, schedule);
		}
	}

	/** 清除预览。 */
	public static void clearPreview() {
		previewRail = null;
		previewBlocks = java.util.Collections.emptyList();
	}

	public static boolean isActive() {
		return previewRail != null && !previewBlocks.isEmpty();
	}

	private static void render(WorldRenderContext context) {
		if (!isActive()) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		final VertexConsumerProvider consumers = context.consumers();
		final MatrixStack matrixStack = context.matrixStack();
		if (consumers == null || matrixStack == null) {
			return;
		}

		final VertexConsumer vertexConsumer = consumers.getBuffer(RenderLayer.getTranslucent());
		for (final CompositeBuilder.PreviewBlock pb : previewBlocks) {
			final net.minecraft.block.BlockState state = pb.state;
			if (state == null || state.getBlock() == Blocks.AIR) {
				continue;
			}
			matrixStack.push();
			client.getBlockRenderManager().renderBlock(
					state, pb.pos, client.world, matrixStack, vertexConsumer,
					false, client.world.random);
			matrixStack.pop();
		}
	}
}
