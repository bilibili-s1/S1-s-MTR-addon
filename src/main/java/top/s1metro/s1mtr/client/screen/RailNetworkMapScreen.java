package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Position;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.screen.MTRScreenBase;

/**
 * 轨道连接工具的地图界面。
 * <p>
 * 复用 MTR 铁路仪表板的地图组件（{@link RailNetworkMapWidget}），
 * 显示底图、车站/车辆段区域标注，并在其上叠加轨道节点圆点。
 * 玩家依次点击两个节点后弹出 {@link RailConnectSpeedScreen} 输入限速并连接。
 */
public class RailNetworkMapScreen extends MTRScreenBase {

	private static final int TOP_BAR_H = 28;

	private RailNetworkMapWidget widget;

	@Override
	protected void init2() {
		super.init2();
		widget = new RailNetworkMapWidget(this::onConnectRequested);
		widget.setPositionAndSize(4, TOP_BAR_H, width - 8, height - TOP_BAR_H - 4);
		addChild(new ClickableWidget(widget));
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.rail_connector.map_title"), width / 2, 8, 0xFFFFFF);
		final Position selected = widget == null ? null : widget.getSelectedNode();
		final String hintKey = selected == null
				? "gui.s1mtr.rail_connector.map_hint"
				: "gui.s1mtr.rail_connector.select_second";
		final Object[] args = selected == null
				? new Object[]{widget == null ? 0 : widget.getRailCount(), widget == null ? 0 : widget.getNodeCount()}
				: new Object[]{selected.getX(), selected.getY(), selected.getZ()};
		graphicsHolder.drawCenteredText(TextHelper.translatable(hintKey, args), width / 2, 18, 0xAAAAAA);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	private void onConnectRequested(Position position1, Position position2) {
		MinecraftClient.getInstance().setScreen(new RailConnectSpeedScreen(position1, position2));
	}

	@Override
	public void onClose2() {
		if (widget != null) {
			widget.onClose();
		}
		super.onClose2();
	}
}
