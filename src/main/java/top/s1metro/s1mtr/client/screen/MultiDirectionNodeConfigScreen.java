package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlockEntity;
import top.s1metro.s1mtr.network.PacketS1mtrSetNodeDirection;

/**
 * 万向节点配置界面（最小可用版）：方向角度输入框 + 旋转绑定开关 + 完成。
 * <p>
 * 方速原版还有平移 / 俯仰 / 翻滚 / 外轨超高 / 轨道编辑等，这些依赖其自研几何内核，本模组不移植。
 * 即时保存（不点完成也生效），客户端本地更新 + C2S 包同步到服务端。
 */
public class MultiDirectionNodeConfigScreen extends MTRScreenBase {

	private static final int PANEL_W = 280;
	private static final int PANEL_H = 132;

	private final BlockPos pos;
	private TextFieldWidgetExtension directionField;
	private boolean bonded;

	public MultiDirectionNodeConfigScreen(BlockPos pos) {
		super();
		this.pos = pos;
		final MultiDirectionNodeBlockEntity node = getNode();
		this.bonded = node != null && node.isDirectionBonded();
	}

	private MultiDirectionNodeBlockEntity getNode() {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return null;
		}
		return client.world.getBlockEntity(pos) instanceof MultiDirectionNodeBlockEntity node ? node : null;
	}

	@Override
	protected void init2() {
		super.init2();
		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;
		final MultiDirectionNodeBlockEntity node = getNode();

		directionField = new TextFieldWidgetExtension(
				panelX + 150, panelY + 32, 110, 20, 12,
				TextCase.DEFAULT, "[^0-9.\\-]", "0");
		directionField.setText2(node == null ? "0" : String.format("%.2f", node.getDirectionDegrees()));
		addChild(new ClickableWidget(directionField));

		final MutableText bindLabel = TextHelper.literal(
				(bonded ? "\u2713 " : "\u25CB ")
						+ TextHelper.translatable("gui.s1mtr.multi_direction_node.rotate_bind").getString());
		final ButtonWidgetExtension bindButton = new ButtonWidgetExtension(
				panelX + 20, panelY + 62, PANEL_W - 40, 20, bindLabel,
				btn -> {
					bonded = !bonded;
					save();
					MinecraftClient.getInstance().setScreen(new MultiDirectionNodeConfigScreen(pos));
				});
		addChild(new ClickableWidget(bindButton));

		final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(
				panelX + 20, panelY + PANEL_H - 28, PANEL_W - 40, 20,
				TextHelper.translatable("gui.done"), btn -> {
					save();
					MinecraftClient.getInstance().setScreen(null);
				});
		addChild(new ClickableWidget(doneButton));
	}

	private void save() {
		final MultiDirectionNodeBlockEntity node = getNode();
		if (node == null) {
			return;
		}
		double degrees;
		try {
			degrees = Double.parseDouble(directionField.getText2().trim());
		} catch (NumberFormatException ignored) {
			degrees = node.getDirectionDegrees();
		}
		degrees = MultiDirectionNodeBlockEntity.wrapDirectionDegrees(degrees);
		node.setDirectionUnbound(degrees);
		node.setRotationBonded(bonded);
		top.s1metro.s1mtr.client.S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
				new PacketS1mtrSetNodeDirection(pos, degrees, bonded));
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);
		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;

		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFE202028);
		guiDrawing.drawRectangle(panelX, panelY, panelX + PANEL_W, panelY + 1, 0xFFFFFFFF);
		guiDrawing.drawRectangle(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, 0xFF404050);
		guiDrawing.drawRectangle(panelX, panelY, panelX + 1, panelY + PANEL_H, 0xFF808090);
		guiDrawing.drawRectangle(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xFF808090);
		guiDrawing.finishDrawingRectangle();

		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.multi_direction_node.title"), width / 2, panelY + 10, 0xFFFFFF);
		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.multi_direction_node.direction"), panelX + 20, panelY + 38,
				-1, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.multi_direction_node.hint"), width / 2, panelY + 94, 0x888888);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}
}
