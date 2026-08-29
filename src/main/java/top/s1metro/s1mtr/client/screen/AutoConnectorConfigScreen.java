package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.item.ItemRailConnectorAuto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 自动速度连接器的配置界面(Shift+右键打开)。
 * <p>
 * 可配置：轨道样式(<b>多选</b>)、是否单向、连接后是否自动复合放样。
 * 配置内容持久保存到该物品自身的 NBT，连接时在 {@code ItemRailConnectorAuto.onConnect} 应用。
 */
public class AutoConnectorConfigScreen extends MTRScreenBase {

	private static final int PANEL_W = 280;
	private static final int PANEL_H = 240;
	private static final int LIST_TOP = 70;
	private static final int LIST_BOTTOM = PANEL_H - 100;
	private static final int STYLE_BTN_H = 18;

	private final org.mtr.mapping.holder.ItemStack stack;
	private final List<String> allStyles = new ArrayList<>();
	private int styleScroll = 0;

	public AutoConnectorConfigScreen(net.minecraft.item.ItemStack stack) {
		super();
		this.stack = new org.mtr.mapping.holder.ItemStack(stack);
		enumerateStyles();
	}

	private void enumerateStyles() {
		final Set<String> set = new LinkedHashSet<>();
		final ObjectImmutableList<RailResource> rails = CustomResourceLoader.getRails();
		if (rails != null) {
			for (RailResource rail : rails) {
				final String id = RailResource.getIdWithoutDirection(rail.getId());
				if (!id.isEmpty()) {
					set.add(id);
				}
			}
		}
		if (set.isEmpty()) {
			set.add("default");
		}
		allStyles.addAll(set);
	}

	@Override
	protected void init2() {
		super.init2();
		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;

		final Set<String> selected = ItemRailConnectorAuto.getStyles(stack);

		// 样式多选列表(复选):点击切换选中状态,可同时选中多个
		for (int i = 0; i < allStyles.size(); i++) {
			final String style = allStyles.get(i);
			final int y = panelY + LIST_TOP + (i - styleScroll) * (STYLE_BTN_H + 2);
			if (y < panelY + LIST_TOP || y > panelY + LIST_BOTTOM) {
				continue;
			}
			final boolean isSelected = selected.contains(style);
			final MutableText label = TextHelper.literal((isSelected ? "\u2713 " : "\u25CB ") + style);
			final ButtonWidgetExtension button = new ButtonWidgetExtension(
					panelX + 20, y, PANEL_W - 40, STYLE_BTN_H, label, btn -> toggleStyle(style));
			addChild(new ClickableWidget(button));
		}

		// 是否单向
		final boolean oneWay = ItemRailConnectorAuto.isOneWay(stack);
		final ButtonWidgetExtension oneWayButton = new ButtonWidgetExtension(
				panelX + 20, panelY + PANEL_H - 92, PANEL_W - 40, 20,
				TextHelper.literal((oneWay ? "\u2713 " : "\u25CB ") + TextHelper.translatable("gui.s1mtr.auto_connector.one_way").getString()),
				btn -> {
					ItemRailConnectorAuto.setOneWay(stack, !ItemRailConnectorAuto.isOneWay(stack));
					sendConfigToServer();
					MinecraftClient.getInstance().setScreen(new AutoConnectorConfigScreen(stack.data));
				});
		addChild(new ClickableWidget(oneWayButton));

		// 自动放样:点击进入复合构建剖面编辑界面(进入即启用自动放样)
		final ButtonWidgetExtension autoBuildButton = new ButtonWidgetExtension(
				panelX + 20, panelY + PANEL_H - 68, PANEL_W - 40, 20,
				TextHelper.translatable("gui.s1mtr.auto_connector.edit_profile"),
				btn -> openProfileEditor());
		addChild(new ClickableWidget(autoBuildButton));

		// 完成
		final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(
				panelX + 20, panelY + PANEL_H - 24, PANEL_W - 40, 20,
				TextHelper.translatable("gui.done"), btn -> MinecraftClient.getInstance().setScreen(null));
		addChild(new ClickableWidget(doneButton));
	}

	/** 打开复合构建剖面编辑界面,编辑结果写回物品 NBT(此后连接会自动放样)。 */
	private void openProfileEditor() {
		final top.s1metro.s1mtr.client.builder.CompositeLayerSchedule schedule =
				ItemRailConnectorAuto.getSchedule(stack);
		top.s1metro.s1mtr.client.screen.CompositeProfileEditorScreen.openForConfig(schedule, edited -> {
			ItemRailConnectorAuto.setSchedule(stack, edited);
			sendConfigToServer();
			MinecraftClient.getInstance().setScreen(new AutoConnectorConfigScreen(stack.data));
		});
	}

	private void toggleStyle(String style) {
		final Set<String> selected = ItemRailConnectorAuto.getStyles(stack);
		if (selected.contains(style)) {
			selected.remove(style);
		} else {
			selected.add(style);
		}
		ItemRailConnectorAuto.setStyles(stack, selected);
		sendConfigToServer();
		MinecraftClient.getInstance().setScreen(new AutoConnectorConfigScreen(stack.data));
	}

	/** 把配置同步到服务端手持物品 NBT,防止服务端重新同步背包时覆盖客户端修改。 */
	private void sendConfigToServer() {
		top.s1metro.s1mtr.client.S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
				new top.s1metro.s1mtr.network.PacketS1mtrSaveAutoConnectorConfig(
						String.join(",", ItemRailConnectorAuto.getStyles(stack)),
						ItemRailConnectorAuto.isOneWay(stack),
						ItemRailConnectorAuto.getSchedule(stack).serialize()));
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
				TextHelper.translatable("gui.s1mtr.auto_connector.config_title"), width / 2, panelY + 10, 0xFFFFFF);
		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.auto_connector.config_style"), width / 2, panelY + 30, 0xAAAAAA);
		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.auto_connector.config_style_hint"), width / 2, panelY + 42, 0x888888);
		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.auto_connector.config_other"), panelX + 20, panelY + PANEL_H - 110,
				-1, false, GraphicsHolder.getDefaultLight());

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}
}
