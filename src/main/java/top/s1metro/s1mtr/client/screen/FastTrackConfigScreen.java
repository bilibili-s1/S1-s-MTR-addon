package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;
import top.s1metro.s1mtr.item.ItemFastTrackBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 快速建造轨道工具的配置界面。
 * <p>
 * 可设置轨道限速、轨道样式与复合构建剖面。配置内容保存到物品自身的 NBT。
 */
public class FastTrackConfigScreen extends MTRScreenBase {

	private static final int PANEL_W = 280;
	private static final int PANEL_H = 210;
	private static final int LIST_TOP = 84;
	private static final int LIST_BOTTOM = PANEL_H - 66;
	private static final int STYLE_BTN_H = 18;

	private final ItemStack stack;
	private final CompositeLayerSchedule schedule;
	private TextFieldWidgetExtension speedField;
	private final List<String> styles = new ArrayList<>();
	private int styleScroll = 0;

	public FastTrackConfigScreen(ItemStack stack) {
		super();
		this.stack = stack;
		this.schedule = ItemFastTrackBuilder.getSchedule(stack);
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
		styles.addAll(set);
	}

	@Override
	protected void init2() {
		super.init2();
		final int panelX = (width - PANEL_W) / 2;
		final int panelY = (height - PANEL_H) / 2;

		speedField = new TextFieldWidgetExtension(
				panelX + 90, panelY + 32, 120, 20, 6, TextCase.DEFAULT, "[^\\d]", "80");
		speedField.setText2(String.valueOf(ItemFastTrackBuilder.getSpeed(stack)));
		addChild(new ClickableWidget(speedField));

		final java.util.Set<String> selectedStyles = ItemFastTrackBuilder.getStyles(stack);

		// 样式多选列表(复选):点击切换选中状态,可同时选中多个
		for (int i = 0; i < styles.size(); i++) {
			final String style = styles.get(i);
			final int y = panelY + LIST_TOP + (i - styleScroll) * (STYLE_BTN_H + 2);
			if (y < panelY + LIST_TOP || y > panelY + LIST_BOTTOM) {
				continue;
			}
			final boolean isSelected = selectedStyles.contains(style);
			final MutableText label = TextHelper.literal((isSelected ? "\u2713 " : "\u25CB ") + style);
			final ButtonWidgetExtension button = new ButtonWidgetExtension(
					panelX + 20, y, PANEL_W - 40, STYLE_BTN_H, label, btn -> toggleStyle(style));
			addChild(new ClickableWidget(button));
		}

		// 剖面编辑
		final MutableText profileLabel = TextHelper.translatable("gui.s1mtr.fasttrack.profile");
		final ButtonWidgetExtension profileButton = new ButtonWidgetExtension(
				panelX + 20, panelY + PANEL_H - 44, PANEL_W - 40, 20, profileLabel,
				btn -> openProfileEditor());
		addChild(new ClickableWidget(profileButton));

		// 完成
		final MutableText doneLabel = TextHelper.translatable("gui.done");
		final ButtonWidgetExtension doneButton = new ButtonWidgetExtension(
				panelX + 20, panelY + PANEL_H - 20, PANEL_W - 40, 20, doneLabel,
				btn -> saveAndClose());
		addChild(new ClickableWidget(doneButton));
	}

	private void toggleStyle(String style) {
		final java.util.Set<String> selected = ItemFastTrackBuilder.getStyles(stack);
		if (selected.contains(style)) {
			selected.remove(style);
		} else {
			selected.add(style);
		}
		ItemFastTrackBuilder.setStyles(stack, selected);
		sendConfigToServer();
		MinecraftClient.getInstance().setScreen(new FastTrackConfigScreen(stack));
	}

	private void openProfileEditor() {
		saveSpeedToNbt();
		CompositeProfileEditorScreen.openForConfig(schedule, edited -> {
			ItemFastTrackBuilder.setSchedule(stack, edited);
			sendConfigToServer();
			MinecraftClient.getInstance().setScreen(new FastTrackConfigScreen(stack));
		});
	}

	private void saveSpeedToNbt() {
		final String text = speedField.getText2().trim();
		long speed = 80;
		try {
			speed = Math.max(1, Long.parseLong(text));
		} catch (NumberFormatException ignored) {
		}
		ItemFastTrackBuilder.setSpeed(stack, speed);
	}

	/** 把配置同步到服务端物品 NBT, 防止服务端重新同步背包时覆盖客户端修改。 */
	private void sendConfigToServer() {
		top.s1metro.s1mtr.client.S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(
				new top.s1metro.s1mtr.network.PacketS1mtrSaveFastTrackConfig(
						ItemFastTrackBuilder.getSpeed(stack),
						String.join(",", ItemFastTrackBuilder.getStyles(stack)),
						ItemFastTrackBuilder.getSchedule(stack).serialize()));
	}

	private void saveAndClose() {
		saveSpeedToNbt();
		ItemFastTrackBuilder.setSchedule(stack, schedule);
		sendConfigToServer();
		MinecraftClient.getInstance().setScreen(null);
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
				TextHelper.translatable("gui.s1mtr.fasttrack.title"), width / 2, panelY + 10, 0xFFFFFF);
		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.fasttrack.speed"), panelX + 20, panelY + 35,
				-1, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.fasttrack.style"), width / 2, panelY + 58, 0xAAAAAA);
		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.fasttrack.hint"), width / 2, panelY + 66, 0x888888);

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}
}
