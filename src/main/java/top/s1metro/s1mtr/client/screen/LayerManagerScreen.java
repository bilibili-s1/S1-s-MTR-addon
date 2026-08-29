package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;
import top.s1metro.s1mtr.client.screen.CompositeProfileEditorScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 分层管理界面。
 * <p>
 * 列出当前 schedule 中的所有剖面条目,每行提供:
 * <ul>
 *   <li>剖面序号</li>
 *   <li>长度输入框(格数,0=无限,仅单层时有效)</li>
 *   <li>上移/下移/删除按钮</li>
 * </ul>
 * 顶部提供"添加剖面"按钮(最多 {@link CompositeLayerSchedule#MAX_LAYERS} 个)。
 * 底部提供"完成"按钮返回上一屏。
 */
public class LayerManagerScreen extends MTRScreenBase {

	private static CompositeLayerSchedule s1mtr$schedule;
	private static Consumer<CompositeLayerSchedule> s1mtr$callback;
	/** 当前在复合构建主界面选中的剖面索引,用于在此界面高亮;复制剖面后更新为复制项。 */
	private static int s1mtr$highlightLayer = 0;

	private CompositeLayerSchedule schedule;
	private final List<TextFieldWidgetExtension> lengthFields = new ArrayList<>();
	private final List<ButtonWidgetExtension> upButtons = new ArrayList<>();
	private final List<ButtonWidgetExtension> downButtons = new ArrayList<>();
	private final List<ButtonWidgetExtension> deleteButtons = new ArrayList<>();

	private static final int ROW_HEIGHT = 24;
	private static final int ROW_TOP = 40;
	private static final int ROW_LEFT = 20;
	private static final int LABEL_W = 60;
	private static final int FIELD_W = 60;
	private static final int BTN_W = 40;
	private static final int BTN_GAP = 4;

	public static void open(CompositeLayerSchedule schedule, Consumer<CompositeLayerSchedule> onDone) {
		s1mtr$schedule = schedule == null ? new CompositeLayerSchedule() : schedule.copy();
		s1mtr$callback = onDone;
		s1mtr$highlightLayer = CompositeProfileEditorScreen.currentLayer;
		MinecraftClient.getInstance().setScreen(new LayerManagerScreen());
	}

	@Override
	protected void init2() {
		super.init2();
		if (schedule == null) {
			schedule = s1mtr$schedule.copy();
		}

		rebuildRows();

		// 添加按钮
		final int addY = ROW_TOP + schedule.size() * ROW_HEIGHT + 8;
		addChild(new ClickableWidget(new ButtonWidgetExtension(
				ROW_LEFT, addY, 100, 20,
				TextHelper.translatable("gui.s1mtr.profile.add_layer"), button -> {
			if (schedule.size() < CompositeLayerSchedule.MAX_LAYERS) {
				schedule.addLayer();
				s1mtr$schedule = schedule.copy();
				MinecraftClient.getInstance().setScreen(new LayerManagerScreen());
			}
		})));

		// 完成按钮
		addChild(new ClickableWidget(new ButtonWidgetExtension(
				width / 2 - 100, height - 28, 200, 20,
				TextHelper.translatable("gui.done"), button -> {
			commitLengths();
			final Consumer<CompositeLayerSchedule> cb = s1mtr$callback;
			s1mtr$callback = null;
			s1mtr$schedule = null;
			if (cb != null) {
				cb.accept(schedule);
			}
		})));
	}

	private void rebuildRows() {
		lengthFields.clear();
		upButtons.clear();
		downButtons.clear();
		deleteButtons.clear();

		for (int i = 0; i < schedule.size(); i++) {
			final int index = i;
			final int y = ROW_TOP + i * ROW_HEIGHT;

			final TextFieldWidgetExtension field = new TextFieldWidgetExtension(
					ROW_LEFT + LABEL_W + 4, y, FIELD_W, 20, 4,
					org.mtr.mapping.tool.TextCase.DEFAULT,
					"[^\\d]",
					"");
			field.setText2(String.valueOf(schedule.get(i).length));
			addChild(new ClickableWidget(field));
			lengthFields.add(field);

			final ButtonWidgetExtension upBtn = new ButtonWidgetExtension(
					ROW_LEFT + LABEL_W + FIELD_W + 8 + 0 * (BTN_W + BTN_GAP), y, BTN_W, 20,
					TextHelper.literal("\u25B2"), button -> {
				if (index > 0) {
					schedule.swap(index, index - 1);
					commitLengths();
					s1mtr$schedule = schedule.copy();
					MinecraftClient.getInstance().setScreen(new LayerManagerScreen());
				}
			});
			if (index == 0) {
				upBtn.setVisibleMapped(false);
			}
			addChild(new ClickableWidget(upBtn));
			upButtons.add(upBtn);

			final ButtonWidgetExtension downBtn = new ButtonWidgetExtension(
					ROW_LEFT + LABEL_W + FIELD_W + 8 + 1 * (BTN_W + BTN_GAP), y, BTN_W, 20,
					TextHelper.literal("\u25BC"), button -> {
				if (index < schedule.size() - 1) {
					schedule.swap(index, index + 1);
					commitLengths();
					s1mtr$schedule = schedule.copy();
					MinecraftClient.getInstance().setScreen(new LayerManagerScreen());
				}
			});
			if (index == schedule.size() - 1) {
				downBtn.setVisibleMapped(false);
			}
			addChild(new ClickableWidget(downBtn));
			downButtons.add(downBtn);

			final ButtonWidgetExtension delBtn = new ButtonWidgetExtension(
					ROW_LEFT + LABEL_W + FIELD_W + 8 + 2 * (BTN_W + BTN_GAP), y, BTN_W, 20,
					TextHelper.literal("\u2715"), button -> {
				if (schedule.size() > 1) {
					schedule.removeLayer(index);
					commitLengths();
					s1mtr$schedule = schedule.copy();
					MinecraftClient.getInstance().setScreen(new LayerManagerScreen());
				}
			});
			if (schedule.size() <= 1) {
				delBtn.setVisibleMapped(false);
			}
			addChild(new ClickableWidget(delBtn));
			deleteButtons.add(delBtn);

			final int editCopyBaseX = ROW_LEFT + LABEL_W + FIELD_W + 8 + 3 * (BTN_W + BTN_GAP);
			final ButtonWidgetExtension editBtn = new ButtonWidgetExtension(
					editCopyBaseX, y, BTN_W, 20,
					TextHelper.translatable("gui.s1mtr.profile.edit"), button -> {
				// 编辑此剖面:记录当前选中索引,关闭本屏并打开复合构建主界面(保留 currentLayer)
				CompositeProfileEditorScreen.currentLayer = index;
				commitLengths();
				s1mtr$schedule = schedule.copy();
				MinecraftClient.getInstance().setScreen(new CompositeProfileEditorScreen());
			});
			addChild(new ClickableWidget(editBtn));

			final ButtonWidgetExtension copyBtn = new ButtonWidgetExtension(
					editCopyBaseX + (BTN_W + BTN_GAP), y, BTN_W, 20,
					TextHelper.translatable("gui.s1mtr.profile.copy"), button -> {
				if (schedule.size() < CompositeLayerSchedule.MAX_LAYERS) {
					// 深拷贝当前剖面,追加到末尾,并自动切换/高亮到复制项
					schedule.addLayer(schedule.get(index).profile.copy());
					s1mtr$highlightLayer = schedule.size() - 1;
					CompositeProfileEditorScreen.currentLayer = s1mtr$highlightLayer;
					commitLengths();
					s1mtr$schedule = schedule.copy();
					MinecraftClient.getInstance().setScreen(new LayerManagerScreen());
				}
			});
			addChild(new ClickableWidget(copyBtn));
		}
	}

	private void commitLengths() {
		for (int i = 0; i < lengthFields.size() && i < schedule.size(); i++) {
			try {
				final int length = Integer.parseInt(lengthFields.get(i).getText2());
				schedule.setLength(i, Math.max(0, length));
			} catch (NumberFormatException ignored) {
			}
		}
	}

	@Override
	public boolean keyPressed2(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			commitLengths();
			final Consumer<CompositeLayerSchedule> cb = s1mtr$callback;
			s1mtr$callback = null;
			s1mtr$schedule = null;
			if (cb != null) {
				cb.accept(schedule);
			}
			return true;
		}
		return super.keyPressed2(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.profile.layer_manager"),
				width / 2, 16,
				-1);

		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.profile.layer_count", schedule.size()),
				ROW_LEFT, ROW_TOP - 14,
				0xFFA0A0A0, false,
				GraphicsHolder.getDefaultLight());

		for (int i = 0; i < schedule.size(); i++) {
			final int y = ROW_TOP + i * ROW_HEIGHT;
			// 高亮当前选中的剖面:仅用极淡的底色,避免遮挡该行的输入框/按钮
			if (i == s1mtr$highlightLayer) {
				final GuiDrawing hl = new GuiDrawing(graphicsHolder);
				hl.beginDrawingRectangle();
				hl.drawRectangle(ROW_LEFT - 4, y - 2,
						ROW_LEFT + LABEL_W + FIELD_W + 8 + 5 * (BTN_W + BTN_GAP) + 4, y + ROW_HEIGHT - 2,
						0x1155AAFF);
				hl.finishDrawingRectangle();
			}
			graphicsHolder.drawText(
					TextHelper.translatable("gui.s1mtr.profile.layer_n", i + 1),
					ROW_LEFT, y + 6,
					-1, false,
					GraphicsHolder.getDefaultLight());
		}

		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.profile.length_hint"),
				ROW_LEFT, height - 50,
				0xFF808080, false,
				GraphicsHolder.getDefaultLight());

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}
}
