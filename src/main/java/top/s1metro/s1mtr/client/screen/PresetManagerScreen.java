package top.s1metro.s1mtr.client.screen;

import net.minecraft.client.MinecraftClient;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;
import top.s1metro.s1mtr.client.builder.ProfileStore;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 预设管理界面。
 * <p>
 * 列出 {@link ProfileStore#getProfilesDir()} 下所有预设文件,每行显示:
 * <ul>
 *   <li>预设名</li>
 *   <li>"取用"按钮:加载预设并返回上层编辑器</li>
 *   <li>"删除"按钮:删除该预设文件</li>
 * </ul>
 * 支持滚轮翻页,底部提供"返回"按钮。
 */
public class PresetManagerScreen extends MTRScreenBase {

	private static Consumer<CompositeLayerSchedule> s1mtr$callback;

	private static final int ROW_HEIGHT = 22;
	private static final int ROW_TOP = 36;
	private static final int ROW_LEFT = 10;
	private static final int VISIBLE_ROWS = 12;
	private static final int BTN_W = 60;
	private static final int BTN_GAP = 4;

	private final List<File> presetFiles = new ArrayList<>();
	private final List<ButtonWidgetExtension> applyButtons = new ArrayList<>();
	private final List<ButtonWidgetExtension> deleteButtons = new ArrayList<>();
	private int scrollOffset = 0;

	public static void open(Consumer<CompositeLayerSchedule> onApply) {
		s1mtr$callback = onApply;
		MinecraftClient.getInstance().setScreen(new PresetManagerScreen());
	}

	@Override
	protected void init2() {
		super.init2();
		refreshList();

		addChild(new ClickableWidget(new ButtonWidgetExtension(
				width / 2 - 100, height - 28, 200, 20,
				TextHelper.translatable("gui.back"), button -> {
			s1mtr$callback = null;
			MinecraftClient.getInstance().setScreen(null);
		})));
	}

	private void refreshList() {
		applyButtons.clear();
		deleteButtons.clear();
		presetFiles.clear();
		presetFiles.addAll(ProfileStore.listPresets());
		scrollOffset = 0;

		for (int i = 0; i < VISIBLE_ROWS; i++) {
			final int index = i;
			final int y = ROW_TOP + i * ROW_HEIGHT;
			final int rightEdge = width - 10;
			final int deleteX = rightEdge - BTN_W;
			final int applyX = deleteX - BTN_W - BTN_GAP;

			final ButtonWidgetExtension applyBtn = new ButtonWidgetExtension(
					applyX, y, BTN_W, 20,
					TextHelper.translatable("gui.s1mtr.profile.apply"), button -> applyPreset(index));
			addChild(new ClickableWidget(applyBtn));
			applyButtons.add(applyBtn);

			final ButtonWidgetExtension deleteBtn = new ButtonWidgetExtension(
					deleteX, y, BTN_W, 20,
					TextHelper.translatable("gui.s1mtr.profile.delete"), button -> deletePreset(index));
			addChild(new ClickableWidget(deleteBtn));
			deleteButtons.add(deleteBtn);
		}
		updateButtonVisibility();
	}

	private void updateButtonVisibility() {
		for (int i = 0; i < VISIBLE_ROWS; i++) {
			final int fileIndex = scrollOffset + i;
			final boolean exists = fileIndex < presetFiles.size();
			applyButtons.get(i).setVisibleMapped(exists);
			deleteButtons.get(i).setVisibleMapped(exists);
		}
	}

	private void applyPreset(int visibleIndex) {
		final int fileIndex = scrollOffset + visibleIndex;
		if (fileIndex < 0 || fileIndex >= presetFiles.size()) {
			return;
		}
		final CompositeLayerSchedule loaded = ProfileStore.loadPreset(presetFiles.get(fileIndex));
		if (loaded != null) {
			final Consumer<CompositeLayerSchedule> cb = s1mtr$callback;
			s1mtr$callback = null;
			if (cb != null) {
				cb.accept(loaded);
			}
		}
	}

	private void deletePreset(int visibleIndex) {
		final int fileIndex = scrollOffset + visibleIndex;
		if (fileIndex < 0 || fileIndex >= presetFiles.size()) {
			return;
		}
		final File file = presetFiles.get(fileIndex);
		if (file.delete()) {
			refreshList();
		}
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		final int maxOffset = Math.max(0, presetFiles.size() - VISIBLE_ROWS);
		final int newOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(amount)));
		if (newOffset != scrollOffset) {
			scrollOffset = newOffset;
			updateButtonVisibility();
		}
		return true;
	}

	@Override
	public boolean keyPressed2(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			s1mtr$callback = null;
			MinecraftClient.getInstance().setScreen(null);
			return true;
		}
		return super.keyPressed2(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.profile.preset_manager"),
				width / 2, 12,
				-1);

		for (int i = 0; i < VISIBLE_ROWS; i++) {
			final int fileIndex = scrollOffset + i;
			if (fileIndex >= presetFiles.size()) {
				break;
			}
			final int y = ROW_TOP + i * ROW_HEIGHT;
			graphicsHolder.drawText(
					TextHelper.literal(ProfileStore.getPresetName(presetFiles.get(fileIndex))),
					ROW_LEFT, y + 6,
					-1, false,
					GraphicsHolder.getDefaultLight());
		}

		// 滚动条
		if (presetFiles.size() > VISIBLE_ROWS) {
			final int trackTop = ROW_TOP;
			final int trackHeight = VISIBLE_ROWS * ROW_HEIGHT;
			final int barHeight = Math.max(10, trackHeight * VISIBLE_ROWS / presetFiles.size());
			final int maxOffset = presetFiles.size() - VISIBLE_ROWS;
			final int barY = trackTop + (maxOffset > 0 ? (int) ((long) (trackHeight - barHeight) * scrollOffset / maxOffset) : 0);
			final int barX = width - 8;
			final org.mtr.mapping.mapper.GuiDrawing guiDrawing = new org.mtr.mapping.mapper.GuiDrawing(graphicsHolder);
			guiDrawing.beginDrawingRectangle();
			guiDrawing.drawRectangle(barX, trackTop, barX + 2, trackTop + trackHeight, 0xFF202020);
			guiDrawing.drawRectangle(barX, barY, barX + 2, barY + barHeight, 0xFF808080);
			guiDrawing.finishDrawingRectangle();
		}

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}
}
