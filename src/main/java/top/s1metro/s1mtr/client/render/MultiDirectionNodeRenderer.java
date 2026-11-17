package top.s1metro.s1mtr.client.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlockEntity;

/**
 * 万向节点标记渲染（最小可用版）。
 * <p>
 * 方速原版用 OBJ 模型 + 自研 sowcer 渲染栈；这里改用零资源的线框立方体 + 方向箭头：
 * 未绑定方向时绕 Y 轴匀速旋转，绑定后按方向固定，已连接时缩小并变暗。
 * 这样不需要任何贴图/OBJ 资源，也不受资源包影响。
 */
public class MultiDirectionNodeRenderer implements BlockEntityRenderer<MultiDirectionNodeBlockEntity> {

	public MultiDirectionNodeRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public void render(MultiDirectionNodeBlockEntity entity, float tickDelta, MatrixStack matrices,
					   VertexConsumerProvider vertexConsumers, int light, int overlay) {
		matrices.push();
		matrices.translate(0.5, 0.5, 0.5);

		final float rotation;
		if (!entity.isDirectionBonded()) {
			rotation = (float) ((System.currentTimeMillis() % 2000) / 2000.0 * Math.PI * 2);
		} else {
			// 与 MTR 节点显示一致：direction 是顺时针罗盘角，rotateY 取负
			rotation = (float) (-Math.toRadians(entity.getDirectionDegrees()) + Math.PI / 2);
		}
		matrices.multiply(RotationAxis.POSITIVE_Y.rotation(rotation));

		final boolean connected = entity.isConnected();
		final float half = connected ? 0.14f : 0.28f;
		final float r = connected ? 0.45f : 0.85f;
		final float g = connected ? 0.45f : 0.35f;
		final float b = connected ? 0.50f : 1.0f;
		final float a = 1.0f;

		final VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getLines());
		final MatrixStack.Entry entry = matrices.peek();

		// 线框立方体
		line(entry, buffer, -half, -half, -half, half, -half, -half, r, g, b, a);
		line(entry, buffer, half, -half, -half, half, -half, half, r, g, b, a);
		line(entry, buffer, half, -half, half, -half, -half, half, r, g, b, a);
		line(entry, buffer, -half, -half, half, -half, -half, -half, r, g, b, a);
		line(entry, buffer, -half, half, -half, half, half, -half, r, g, b, a);
		line(entry, buffer, half, half, -half, half, half, half, r, g, b, a);
		line(entry, buffer, half, half, half, -half, half, half, r, g, b, a);
		line(entry, buffer, -half, half, half, -half, half, -half, r, g, b, a);
		line(entry, buffer, -half, -half, -half, -half, half, -half, r, g, b, a);
		line(entry, buffer, half, -half, -half, half, half, -half, r, g, b, a);
		line(entry, buffer, half, -half, half, half, half, half, r, g, b, a);
		line(entry, buffer, -half, -half, half, -half, half, half, r, g, b, a);

		// 方向箭头：沿局部 +Z（节点前进方向），亮青色
		final float tip = half + 0.22f;
		line(entry, buffer, 0, 0, 0, 0, 0, tip, 0.2f, 1.0f, 1.0f, a);
		line(entry, buffer, 0, 0, tip, -0.09f, 0, tip - 0.14f, 0.2f, 1.0f, 1.0f, a);
		line(entry, buffer, 0, 0, tip, 0.09f, 0, tip - 0.14f, 0.2f, 1.0f, 1.0f, a);

		matrices.pop();
	}

	private static void line(MatrixStack.Entry entry, VertexConsumer buffer,
							 float x1, float y1, float z1, float x2, float y2, float z2,
							 float r, float g, float b, float a) {
		final Matrix4f position = entry.getPositionMatrix();
		final Matrix3f normal = entry.getNormalMatrix();
		buffer.vertex(position, x1, y1, z1).color(r, g, b, a).normal(normal, 0, 1, 0).next();
		buffer.vertex(position, x2, y2, z2).color(r, g, b, a).normal(normal, 0, 1, 0).next();
	}
}
