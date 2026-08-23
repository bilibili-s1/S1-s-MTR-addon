package top.s1metro.s1mtr.client.screen;

import org.mtr.core.data.Rail;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.RailSpeedHelper;
import top.s1metro.s1mtr.client.S1mtraddonClient;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;
import top.s1metro.s1mtr.client.builder.CompositeProfile;
import top.s1metro.s1mtr.network.PacketS1mtrBuildComposite;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 复合构建编辑器(全屏,支持鼠标滚轮滚动)。
 * <p>
 * 顶部条:剖面切换 + 添加剖面 + 分层管理 + 预设管理。
 * 中部:14×14 网格(每格 16px),鼠标悬停显示 tooltip。
 * 底部:方块选择按钮(label 含当前方块名) + 旋转图标按钮 + 空气/清除/保存/放样/返回。
 * <p>
 * 数据以 {@link CompositeLayerSchedule} 持有,保存到 rail styles 时通过
 * {@link RailSpeedHelper#copyWithCompositeSchedule} 持久化。
 */
public class CompositeProfileEditorScreen extends MTRScreenBase {

	private static Rail s1mtr$currentRail;
	private static CompositeLayerSchedule s1mtr$schedule;
	private static String s1mtr$selectedBlockState = "minecraft:air";
	/** 快速建造工具等场景下,编辑器返回时执行的回调(用于把编辑后的剖面写回物品 NBT)。 */
	private static java.util.function.Consumer<CompositeLayerSchedule> s1mtr$onReturnCallback;

	/** 网格单元像素尺寸:16px(略缩小)。 */
	private static final int GRID_CELL = 16;
	private static final int GRID_PIXEL = CompositeProfile.GRID_SIZE * GRID_CELL;
	private static final int CONTENT_TOP = 56;
	private static final int SCROLL_STEP = 12;

	/** 顶部条按钮尺寸。 */
	private static final int TOP_BTN_H = 20;
	private static final int TOP_BTN_W = 60;
	private static final int TOP_BTN_GAP = 4;

	/** 下方按钮布局。 */
	private static final int BLOCK_BTN_W = 160;
	private static final int ICON_BTN_W = 24;
	private static final int BTN_HALF_W = (GRID_PIXEL - ICON_BTN_W - 8) / 2;

	private CompositeLayerSchedule schedule;
	private int currentLayer = 0;

	private ButtonWidgetExtension prevLayerButton;
	private ButtonWidgetExtension nextLayerButton;
	private ButtonWidgetExtension addLayerButton;
	private ButtonWidgetExtension layerManagerButton;
	private ButtonWidgetExtension presetManagerButton;
	private ButtonWidgetExtension blockSelectButton;
	private ButtonWidgetExtension clearButton;
	private ButtonWidgetExtension saveButton;
	private ButtonWidgetExtension buildButton;
	private ButtonWidgetExtension savePresetButton;
	private ButtonWidgetExtension backButton;
	private TextFieldWidgetExtension presetNameField;

	private int gridX = -1;
	private int gridY = CONTENT_TOP;
	private int scrollY = 0;
	private String statusMessage = "";

	private static final Field S1MTR_DRAW_CONTEXT_FIELD;

	static {
		Field field = null;
		try {
			field = GraphicsHolder.class.getDeclaredField("drawContext");
			field.setAccessible(true);
		} catch (Exception ignored) {
		}
		S1MTR_DRAW_CONTEXT_FIELD = field;
	}

	public static void open(Rail rail, CompositeLayerSchedule schedule) {
		s1mtr$currentRail = rail;
		s1mtr$schedule = schedule == null ? new CompositeLayerSchedule() : schedule.copy();
		s1mtr$onReturnCallback = null;
		MinecraftClient.getInstance().setScreen(new CompositeProfileEditorScreen());
	}

	/** 快速建造工具专用入口:编辑剖面,返回时通过回调把编辑结果交回,不依赖 Rail。 */
	public static void openForConfig(CompositeLayerSchedule schedule,
									 java.util.function.Consumer<CompositeLayerSchedule> onReturn) {
		s1mtr$currentRail = null;
		s1mtr$schedule = schedule == null ? new CompositeLayerSchedule() : schedule.copy();
		s1mtr$onReturnCallback = onReturn;
		MinecraftClient.getInstance().setScreen(new CompositeProfileEditorScreen());
	}

	/** 兼容旧入口:从单剖面打开(转为单层 schedule)。 */
	public static void open(Rail rail, CompositeProfile profile) {
		final CompositeLayerSchedule schedule = new CompositeLayerSchedule();
		if (profile != null) {
			schedule.entries().get(0).profile = profile;
			schedule.entries().get(0).length = 1;
		}
		open(rail, schedule);
	}

	private Rail getRail() {
		return s1mtr$currentRail;
	}

	private CompositeProfile currentProfile() {
		if (schedule == null || schedule.size() == 0) {
			return new CompositeProfile();
		}
		if (currentLayer < 0 || currentLayer >= schedule.size()) {
			currentLayer = 0;
		}
		return schedule.get(currentLayer).profile;
	}

	private int getContentHeight() {
		return CONTENT_TOP + GRID_PIXEL + 160;
	}

	private int getMinScroll() {
		return Math.min(0, (height - 60) - getContentHeight());
	}

	@Override
	protected void init2() {
		super.init2();
		if (schedule == null) {
			schedule = s1mtr$schedule == null ? new CompositeLayerSchedule() : s1mtr$schedule.copy();
		}
		if (currentLayer >= schedule.size()) {
			currentLayer = 0;
		}

		gridX = (width - GRID_PIXEL) / 2;
		gridY = CONTENT_TOP + scrollY;

		// 顶部条:剖面切换 + 添加 + 分层管理(不含预设管理,预设管理移到下方)
		final int topY = 8;
		final int topCenter = width / 2;
		prevLayerButton = new ButtonWidgetExtension(
				topCenter - TOP_BTN_W - TOP_BTN_GAP - 40, topY, TOP_BTN_W, TOP_BTN_H,
				TextHelper.literal("\u25C0"), button -> {
			if (currentLayer > 0) {
				currentLayer--;
			}
		});
		addChild(new ClickableWidget(prevLayerButton));

		nextLayerButton = new ButtonWidgetExtension(
				topCenter + 40 + TOP_BTN_GAP, topY, TOP_BTN_W, TOP_BTN_H,
				TextHelper.literal("\u25B6"), button -> {
			if (currentLayer < schedule.size() - 1) {
				currentLayer++;
			}
		});
		addChild(new ClickableWidget(nextLayerButton));

		addLayerButton = new ButtonWidgetExtension(
				topCenter + 40 + TOP_BTN_GAP + (TOP_BTN_W + TOP_BTN_GAP) * 1, topY, TOP_BTN_W, TOP_BTN_H,
				TextHelper.literal("+"), button -> {
			if (schedule.size() < CompositeLayerSchedule.MAX_LAYERS) {
				schedule.addLayer();
				currentLayer = schedule.size() - 1;
				s1mtr$schedule = schedule.copy();
				MinecraftClient.getInstance().setScreen(new CompositeProfileEditorScreen());
			}
		});
		addChild(new ClickableWidget(addLayerButton));

		layerManagerButton = new ButtonWidgetExtension(
				topCenter - 40 - TOP_BTN_W - TOP_BTN_GAP - (TOP_BTN_W + TOP_BTN_GAP) * 2, topY, TOP_BTN_W * 2, TOP_BTN_H,
				TextHelper.translatable("gui.s1mtr.profile.layer_manager"), button -> {
			saveProfileSilent();
			LayerManagerScreen.open(schedule, result -> {
				schedule = result;
				s1mtr$schedule = schedule.copy();
				if (currentLayer >= schedule.size()) {
					currentLayer = schedule.size() - 1;
				}
				MinecraftClient.getInstance().setScreen(new CompositeProfileEditorScreen());
			});
		});
		addChild(new ClickableWidget(layerManagerButton));

		// 下方按钮
		final int belowGrid = gridY + GRID_PIXEL;

		blockSelectButton = new ButtonWidgetExtension(
				gridX, belowGrid + 18, GRID_PIXEL, 20,
				TextHelper.literal(buildBlockSelectLabel()),
				button -> openBlockPicker());
		addChild(new ClickableWidget(blockSelectButton));

		clearButton = new ButtonWidgetExtension(
				gridX, belowGrid + 42, GRID_PIXEL, 20,
				TextHelper.translatable("gui.s1mtr.profile.clear"), button -> {
			s1mtr$selectedBlockState = null;
			refreshBlockSelectLabel();
		});
		addChild(new ClickableWidget(clearButton));

		saveButton = new ButtonWidgetExtension(
				gridX, belowGrid + 66, BTN_HALF_W, 20,
				TextHelper.translatable("gui.s1mtr.profile.save"), button -> saveProfile());
		addChild(new ClickableWidget(saveButton));

		buildButton = new ButtonWidgetExtension(
				gridX + BTN_HALF_W + 4, belowGrid + 66, BTN_HALF_W, 20,
				TextHelper.translatable("gui.s1mtr.profile.build"), button -> buildComposite());
		addChild(new ClickableWidget(buildButton));

		// 预设名输入框 + 存为预制 + 预设管理 (移到开始放样下方)
		presetNameField = new TextFieldWidgetExtension(
				gridX, belowGrid + 90, BTN_HALF_W, 20, 32,
				org.mtr.mapping.tool.TextCase.DEFAULT,
				"",
				"my_profile");
		addChild(new ClickableWidget(presetNameField));

		savePresetButton = new ButtonWidgetExtension(
				gridX + BTN_HALF_W + 4, belowGrid + 90, BTN_HALF_W, 20,
				TextHelper.translatable("gui.s1mtr.profile.save_preset"), button -> savePreset());
		addChild(new ClickableWidget(savePresetButton));

		presetManagerButton = new ButtonWidgetExtension(
				gridX, belowGrid + 114, GRID_PIXEL, 20,
				TextHelper.translatable("gui.s1mtr.profile.preset_manager"), button -> {
			saveProfileSilent();
			PresetManagerScreen.open(loaded -> {
				if (loaded != null) {
					schedule.copyFrom(loaded);
					s1mtr$schedule = schedule.copy();
					currentLayer = 0;
				}
				MinecraftClient.getInstance().setScreen(new CompositeProfileEditorScreen());
			});
		});
		addChild(new ClickableWidget(presetManagerButton));

		backButton = new ButtonWidgetExtension(
				width / 2 - 100, height - 40, 200, 20,
				TextHelper.translatable("gui.s1mtr.profile.back"), button -> backToAdvancedSettings());
		addChild(new ClickableWidget(backButton));
	}

	private void updateWidgetPositions() {
		gridY = CONTENT_TOP + scrollY;
		final int belowGrid = gridY + GRID_PIXEL;
		if (blockSelectButton != null) {
			blockSelectButton.setY2(belowGrid + 18);
		}
		if (clearButton != null) {
			clearButton.setY2(belowGrid + 42);
		}
		if (saveButton != null) {
			saveButton.setY2(belowGrid + 66);
		}
		if (buildButton != null) {
			buildButton.setY2(belowGrid + 66);
		}
		if (presetNameField != null) {
			presetNameField.setY2(belowGrid + 90);
		}
		if (savePresetButton != null) {
			savePresetButton.setY2(belowGrid + 90);
		}
		if (presetManagerButton != null) {
			presetManagerButton.setY2(belowGrid + 114);
		}
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		final int minScroll = getMinScroll();
		if (minScroll < 0) {
			final int newScroll = (int) (scrollY + amount * SCROLL_STEP);
			final int clamped = Math.max(minScroll, Math.min(0, newScroll));
			if (clamped != scrollY) {
				scrollY = clamped;
				updateWidgetPositions();
			}
		}
		return true;
	}

	private void saveProfile() {
		saveProfileSilent();
		statusMessage = TextHelper.translatable("gui.s1mtr.profile.saved").getString();
	}

	private void saveProfileSilent() {
		final Rail rail = getRail();
		if (rail != null) {
			final Rail updatedRail = RailSpeedHelper.copyWithCompositeSchedule(rail, schedule);
			if (updatedRail != null) {
				InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(
						new UpdateDataRequest(MinecraftClientData.getInstance())
								.addRail(updatedRail)
				));
			}
		}
	}

	private void savePreset() {
		final String name = presetNameField.getText2();
		if (top.s1metro.s1mtr.client.builder.ProfileStore.savePreset(name, schedule)) {
			statusMessage = TextHelper.translatable("gui.s1mtr.profile.saved").getString();
			presetNameField.setText2("");
		}
	}

	private void buildComposite() {
		saveProfileSilent();
		final Rail rail = getRail();
		if (rail != null) {
			S1mtraddonClient.REGISTRY_CLIENT.sendPacketToServer(new PacketS1mtrBuildComposite(rail, schedule));
		}
	}

	private void backToAdvancedSettings() {
		if (s1mtr$onReturnCallback != null) {
			final java.util.function.Consumer<CompositeLayerSchedule> callback = s1mtr$onReturnCallback;
			s1mtr$onReturnCallback = null;
			callback.accept(schedule.copy());
			return;
		}
		final Rail rail = getRail();
		if (rail != null) {
			RailAdvancedSettingsScreen.open(rail, RailSpeedHelper.getRadius(rail), RailSpeedHelper.getShape(rail));
		} else {
			MinecraftClient.getInstance().setScreen(null);
		}
	}

	private void openBlockPicker() {
		s1mtr$schedule = schedule.copy();
		BlockPickerScreen.open(s1mtr$selectedBlockState, result -> {
			if (result != null) {
				s1mtr$selectedBlockState = result;
			}
			s1mtr$schedule = schedule.copy();
			MinecraftClient.getInstance().setScreen(new CompositeProfileEditorScreen());
		});
	}

	private void cycleSelectedRotation() {
		// 旋转按钮:对当前选中方块的 BlockState 应用 CW90
		final net.minecraft.block.BlockState state = CompositeProfile.parseBlockState(s1mtr$selectedBlockState);
		if (state != null && state.getBlock() != Blocks.AIR) {
			final net.minecraft.block.BlockState rotated = state.rotate(net.minecraft.util.BlockRotation.CLOCKWISE_90);
			s1mtr$selectedBlockState = CompositeProfile.blockStateToString(rotated);
		}
	}

	private void refreshBlockSelectLabel() {
		if (blockSelectButton != null) {
			blockSelectButton.setMessage2(org.mtr.mapping.holder.Text.cast(TextHelper.literal(buildBlockSelectLabel())));
		}
	}

	private String buildBlockSelectLabel() {
		return TextHelper.translatable("gui.s1mtr.profile.select_block").getString()
				+ "[" + getSelectedBlockDisplayName() + "]";
	}

	private String getSelectedBlockDisplayName() {
		if (s1mtr$selectedBlockState == null) {
			return TextHelper.translatable("gui.s1mtr.profile.none").getString();
		}
		final net.minecraft.block.BlockState state = CompositeProfile.parseBlockState(s1mtr$selectedBlockState);
		if (state == null) {
			return s1mtr$selectedBlockState;
		}
		final Block block = state.getBlock();
		if (block == Blocks.AIR) {
			return TextHelper.translatable("gui.s1mtr.profile.air").getString();
		}
		return block.getName().getString();
	}

	@Override
	public boolean keyPressed2(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			backToAdvancedSettings();
			return true;
		}
		return super.keyPressed2(keyCode, scanCode, modifiers);
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		// 顶部剖面指示
		graphicsHolder.drawCenteredText(
				TextHelper.translatable("gui.s1mtr.profile.layer_n_of", currentLayer + 1, schedule.size()),
				width / 2, 32,
				-1);

		if (gridX >= 0) {
			renderProfileEditor(graphicsHolder, mouseX, mouseY);
		}

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	private void renderProfileEditor(GraphicsHolder graphicsHolder, int mouseX, int mouseY) {
		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
		final DrawContext drawContext = getDrawContext(graphicsHolder);

		// 外框
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(gridX - 2, gridY - 2, gridX + GRID_PIXEL + 2, gridY + GRID_PIXEL + 2, 0xFF101010);

		final CompositeProfile profile = currentProfile();

		// 单元格背景
		for (int row = 0; row < CompositeProfile.GRID_SIZE; row++) {
			for (int col = 0; col < CompositeProfile.GRID_SIZE; col++) {
				final int gx = col - CompositeProfile.CENTER;
				final int gy = CompositeProfile.GRID_SIZE - CompositeProfile.CENTER - 1 - row;
				final String cell = profile.getCell(gx, gy);
				final int cellX = gridX + col * GRID_CELL;
				final int cellY = gridY + row * GRID_CELL;

				final int bgColor;
				if (cell == null) {
					bgColor = 0xFF2A2A2A;
				} else if ("minecraft:air".equals(cell)) {
					bgColor = 0xFF3D6FB8;
				} else {
					bgColor = 0xFF1A1A1A;
				}
				guiDrawing.drawRectangle(cellX, cellY, cellX + GRID_CELL, cellY + GRID_CELL, bgColor);
			}
		}
		guiDrawing.finishDrawingRectangle();

		// 方块图标
		if (drawContext != null) {
			for (int row = 0; row < CompositeProfile.GRID_SIZE; row++) {
				for (int col = 0; col < CompositeProfile.GRID_SIZE; col++) {
					final int gx = col - CompositeProfile.CENTER;
					final int gy = CompositeProfile.GRID_SIZE - CompositeProfile.CENTER - 1 - row;
					final String cell = profile.getCell(gx, gy);
					if (cell == null || "minecraft:air".equals(cell)) {
						continue;
					}
					final net.minecraft.block.BlockState state = CompositeProfile.parseBlockState(cell);
					if (state == null) {
						continue;
					}
					final Block block = state.getBlock();
					if (block == Blocks.AIR) {
						continue;
					}
					final int cellX = gridX + col * GRID_CELL;
					final int cellY = gridY + row * GRID_CELL;
					drawContext.drawItem(new ItemStack(block), cellX, cellY);
				}
			}
		}

		// 网格分隔线
		guiDrawing.beginDrawingRectangle();
		for (int i = 0; i <= CompositeProfile.GRID_SIZE; i++) {
			final int p = gridX + i * GRID_CELL;
			guiDrawing.drawRectangle(p, gridY, p + 1, gridY + GRID_PIXEL, 0xFF3A3A3A);
			final int q = gridY + i * GRID_CELL;
			guiDrawing.drawRectangle(gridX, q, gridX + GRID_PIXEL, q + 1, 0xFF3A3A3A);
		}

		// 中心十字参考线:竖线在 gx=0 格中央,横线在 gy=0 与 gy=-1 之间
		final int vLineX = gridX + CompositeProfile.CENTER * GRID_CELL + GRID_CELL / 2;
		guiDrawing.drawRectangle(vLineX, gridY, vLineX + 1, gridY + GRID_PIXEL, 0xFFFFFFFF);
		final int hLineY = gridY + CompositeProfile.CENTER * GRID_CELL;
		guiDrawing.drawRectangle(gridX, hLineY, gridX + GRID_PIXEL, hLineY + 1, 0xFFFFFFFF);

		// 鼠标悬停高亮
		if (isInGrid(mouseX, mouseY)) {
			final int col = (int) ((mouseX - gridX) / GRID_CELL);
			final int row = (int) ((mouseY - gridY) / GRID_CELL);
			guiDrawing.drawRectangle(
					gridX + col * GRID_CELL + 1, gridY + row * GRID_CELL + 1,
					gridX + (col + 1) * GRID_CELL - 1, gridY + (row + 1) * GRID_CELL - 1,
					0x66FFFFFF);
		}
		guiDrawing.finishDrawingRectangle();

		// 文字标签
		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.profile.block"),
				gridX + 2, gridY + GRID_PIXEL + 4,
				-1, false,
				GraphicsHolder.getDefaultLight());

		// 选中方块图标在按钮上
		if (drawContext != null && s1mtr$selectedBlockState != null) {
			final net.minecraft.block.BlockState state = CompositeProfile.parseBlockState(s1mtr$selectedBlockState);
			if (state != null && state.getBlock() != Blocks.AIR) {
				drawContext.drawItem(new ItemStack(state.getBlock()), gridX + 4, gridY + GRID_PIXEL + 21);
			}
		}

		if (!statusMessage.isEmpty()) {
			graphicsHolder.drawText(
					TextHelper.literal(statusMessage),
					gridX + GRID_PIXEL - 80, gridY + GRID_PIXEL + 4,
					0xFF80FF80, false,
					GraphicsHolder.getDefaultLight());
		}

		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.profile.hint"),
				gridX + 2, gridY + GRID_PIXEL + 140,
				0x7FFFFFFF, false,
				GraphicsHolder.getDefaultLight());

		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.profile.legend"),
				gridX + 2, gridY + GRID_PIXEL + 152,
				0x7FFFFFFF, false,
				GraphicsHolder.getDefaultLight());

		// 鼠标悬停 tooltip
		if (isInGrid(mouseX, mouseY)) {
			final int col = (int) ((mouseX - gridX) / GRID_CELL);
			final int row = (int) ((mouseY - gridY) / GRID_CELL);
			if (col >= 0 && col < CompositeProfile.GRID_SIZE && row >= 0 && row < CompositeProfile.GRID_SIZE) {
				final int gx = col - CompositeProfile.CENTER;
				final int gy = CompositeProfile.GRID_SIZE - CompositeProfile.CENTER - 1 - row;
				final String cell = profile.getCell(gx, gy);
				if (cell != null && !"minecraft:air".equals(cell)) {
					if (drawContext != null) {
						final List<Text> tooltipLines = new ArrayList<>();
						final net.minecraft.block.BlockState state = CompositeProfile.parseBlockState(cell);
						if (state != null) {
							tooltipLines.add(state.getBlock().getName());
						}
						tooltipLines.add(Text.literal(cell).formatted(net.minecraft.util.Formatting.GRAY));
						drawContext.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltipLines, (int) mouseX, (int) mouseY);
					}
				}
			}
		}
	}

	private static DrawContext getDrawContext(GraphicsHolder graphicsHolder) {
		if (S1MTR_DRAW_CONTEXT_FIELD == null) {
			return null;
		}
		try {
			return (DrawContext) S1MTR_DRAW_CONTEXT_FIELD.get(graphicsHolder);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	@Override
	public boolean mouseClicked2(double mouseX, double mouseY, int button) {
		if (gridX >= 0 && isInGrid(mouseX, mouseY)) {
			final int col = (int) ((mouseX - gridX) / GRID_CELL);
			final int row = (int) ((mouseY - gridY) / GRID_CELL);
			if (col >= 0 && col < CompositeProfile.GRID_SIZE && row >= 0 && row < CompositeProfile.GRID_SIZE) {
				final int gx = col - CompositeProfile.CENTER;
				final int gy = CompositeProfile.GRID_SIZE - CompositeProfile.CENTER - 1 - row;
				if (button == 1) {
					currentProfile().setCell(gx, gy, null);
					return true;
				} else {
					currentProfile().setCell(gx, gy, s1mtr$selectedBlockState);
					return true;
				}
			}
		}
		return super.mouseClicked2(mouseX, mouseY, button);
	}

	private boolean isInGrid(double mouseX, double mouseY) {
		return mouseX >= gridX && mouseX < gridX + GRID_PIXEL && mouseY >= gridY && mouseY < gridY + GRID_PIXEL;
	}
}
