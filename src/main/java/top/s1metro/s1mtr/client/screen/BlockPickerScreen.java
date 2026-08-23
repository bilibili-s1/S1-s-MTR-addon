package top.s1metro.s1mtr.client.screen;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.screen.MTRScreenBase;
import top.s1metro.s1mtr.client.builder.CompositeProfile;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 方块编辑器界面。
 * <p>
 * 左半部分:方块选择网格(搜索 + 图标网格 + 滚动条),第一格固定为"空气"。
 * 右半部分:选中方块的 BlockState 编辑面板:
 * <ul>
 *   <li>顶部 3D 预览(用 {@link net.minecraft.client.render.block.BlockRenderManager#renderBlockAsItem} 渲染当前 BlockState)</li>
 *   <li>顺时针旋转按钮(应用 {@link BlockState#rotate})</li>
 *   <li>上下翻转按钮(对支持 {@code half} 属性的方块有效)</li>
 *   <li>属性列表:列出方块的所有可编辑属性,点击循环切换值</li>
 * </ul>
 */
public class BlockPickerScreen extends MTRScreenBase {

	private static String s1mtr$initialState;
	private static Consumer<String> s1mtr$callback;

	private static final int COLS = 7;
	private static final int VISIBLE_ROWS = 7;
	private static final int CELL_SIZE = 22;
	private static final int GRID_TOP = 36;
	private static final int GRID_LEFT = 10;
	private static final int RIGHT_PANEL_X = GRID_LEFT + COLS * CELL_SIZE + 20;
	private static final int PREVIEW_SIZE = 48;
	private static final int PREVIEW_X = RIGHT_PANEL_X + 130;
	private static final int PREVIEW_Y = GRID_TOP + 4;
	private static final int PROPS_TOP = PREVIEW_Y + PREVIEW_SIZE + 24;
	private static final int PROPS_ROW_H = 18;
	private static final int VISIBLE_PROPS = 7;

	private TextFieldWidgetExtension searchField;
	private final List<Block> filteredBlocks = new ArrayList<>();
	private int scrollOffset = 0;

	private BlockState currentBlockState;
	private Block currentBlock;
	private int propsScroll = 0;

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

	public static void open(String initialStateString, Consumer<String> onSelect) {
		s1mtr$initialState = initialStateString;
		s1mtr$callback = onSelect;
		MinecraftClient.getInstance().setScreen(new BlockPickerScreen());
	}

	@Override
	protected void init2() {
		super.init2();
		searchField = new TextFieldWidgetExtension(
				GRID_LEFT, 12, COLS * CELL_SIZE, 20, 64,
				org.mtr.mapping.tool.TextCase.DEFAULT,
				"",
				"");
		addChild(new ClickableWidget(searchField));
		searchField.setChangedListener2(this::filterBlocks);

		final BlockState parsed = CompositeProfile.parseBlockState(s1mtr$initialState);
		if (parsed != null) {
			currentBlockState = parsed;
			currentBlock = parsed.getBlock();
		} else {
			currentBlockState = Blocks.STONE.getDefaultState();
			currentBlock = Blocks.STONE;
		}
		filterBlocks("");

		// 旋转 / 上下翻转按钮(放在方块名下方,预览左侧)
		final int rotateBtnY = PREVIEW_Y + 28;
		addChild(new ClickableWidget(new ButtonWidgetExtension(
				RIGHT_PANEL_X, rotateBtnY, 60, 20,
				TextHelper.literal("\u21BB"), button -> {
			if (currentBlockState != null) {
				currentBlockState = currentBlockState.rotate(BlockRotation.CLOCKWISE_90);
			}
		})));
		addChild(new ClickableWidget(new ButtonWidgetExtension(
				RIGHT_PANEL_X + 64, rotateBtnY, 60, 20,
				TextHelper.literal("\u21F5"), button -> {
			if (currentBlockState != null) {
				net.minecraft.state.property.Property<?> halfProp = null;
				for (net.minecraft.state.property.Property<?> p : currentBlockState.getProperties()) {
					if ("half".equals(p.getName())) {
						halfProp = p;
						break;
					}
				}
				if (halfProp != null) {
					currentBlockState = cycleProperty(currentBlockState, halfProp);
				}
			}
		})));

		// 确认 / 取消按钮
		final int rightWidth = Math.max(140, width - RIGHT_PANEL_X - 10);
		final int btnW = (rightWidth - 4) / 2;
		final int confirmBtnY = height - 24;
		addChild(new ClickableWidget(new ButtonWidgetExtension(
				RIGHT_PANEL_X, confirmBtnY, btnW, 20,
				TextHelper.translatable("gui.s1mtr.profile.confirm"), button -> confirm())));
		addChild(new ClickableWidget(new ButtonWidgetExtension(
				RIGHT_PANEL_X + btnW + 4, confirmBtnY, btnW, 20,
				TextHelper.translatable("gui.cancel"), button -> cancel())));
	}

	private int getPropertyCount() {
		return currentBlockState == null ? 0 : currentBlockState.getProperties().size();
	}

	private void filterBlocks(String query) {
		final String q = query == null ? "" : query.toLowerCase().trim();
		filteredBlocks.clear();
		// 第一格固定为空气
		filteredBlocks.add(Blocks.AIR);
		for (final Block block : Registries.BLOCK) {
			if (block == Blocks.AIR) {
				continue;
			}
			final String name = block.getName().getString().toLowerCase();
			final String id = Registries.BLOCK.getId(block).toString();
			if (q.isEmpty() || name.contains(q) || id.contains(q)) {
				filteredBlocks.add(block);
			}
		}
		scrollOffset = 0;
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		if (mouseX < RIGHT_PANEL_X) {
			final int maxRow = Math.max(0, (filteredBlocks.size() + COLS - 1) / COLS - VISIBLE_ROWS);
			final int newOffset = Math.max(0, Math.min(maxRow, scrollOffset - (int) Math.signum(amount)));
			if (newOffset != scrollOffset) {
				scrollOffset = newOffset;
			}
		} else {
			final int total = getPropertyCount();
			if (amount < 0 && propsScroll < total - VISIBLE_PROPS) {
				propsScroll++;
			} else if (amount > 0 && propsScroll > 0) {
				propsScroll--;
			}
		}
		return true;
	}

	@Override
	public boolean mouseClicked2(double mouseX, double mouseY, int button) {
		// 网格点击
		if (mouseY >= GRID_TOP && mouseX >= GRID_LEFT && mouseX < GRID_LEFT + COLS * CELL_SIZE
				&& mouseY < GRID_TOP + VISIBLE_ROWS * CELL_SIZE) {
			final int col = (int) ((mouseX - GRID_LEFT) / CELL_SIZE);
			final int row = (int) ((mouseY - GRID_TOP) / CELL_SIZE);
			if (col >= 0 && col < COLS && row >= 0 && row < VISIBLE_ROWS) {
				final int index = scrollOffset * COLS + row * COLS + col;
				if (index >= 0 && index < filteredBlocks.size()) {
					final Block block = filteredBlocks.get(index);
					currentBlock = block;
					currentBlockState = block.getDefaultState();
					propsScroll = 0;
					return true;
				}
			}
		}

		// 属性列表点击(循环切换)
		if (mouseX >= RIGHT_PANEL_X && mouseY >= PROPS_TOP && mouseY < PROPS_TOP + VISIBLE_PROPS * PROPS_ROW_H) {
			final int row = (int) ((mouseY - PROPS_TOP) / PROPS_ROW_H);
			if (row >= 0 && row < VISIBLE_PROPS) {
				final int propIndex = propsScroll + row;
				final List<Property<?>> props = new ArrayList<>(currentBlockState.getProperties());
				if (propIndex >= 0 && propIndex < props.size()) {
					currentBlockState = cycleProperty(currentBlockState, props.get(propIndex));
					return true;
				}
			}
		}

		return super.mouseClicked2(mouseX, mouseY, button);
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> BlockState cycleProperty(BlockState state, Property<?> prop) {
		final Property<T> typed = (Property<T>) prop;
		final T current = state.get(typed);
		T target = current;
		boolean found = false;
		for (T v : typed.getValues()) {
			if (found) {
				target = v;
				break;
			}
			if (v.equals(current)) {
				found = true;
			}
		}
		if (!found || target == current) {
			if (!typed.getValues().isEmpty()) {
				target = typed.getValues().iterator().next();
			}
		}
		return state.with(typed, target);
	}

	@Override
	public boolean keyPressed2(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 256) {
			cancel();
			return true;
		}
		return super.keyPressed2(keyCode, scanCode, modifiers);
	}

	private void cancel() {
		final Consumer<String> cb = s1mtr$callback;
		s1mtr$callback = null;
		s1mtr$initialState = null;
		if (cb != null) {
			cb.accept(null);
		} else {
			MinecraftClient.getInstance().setScreen(null);
		}
	}

	private void confirm() {
		final Consumer<String> cb = s1mtr$callback;
		final String result = currentBlockState == null ? null : CompositeProfile.blockStateToString(currentBlockState);
		s1mtr$callback = null;
		s1mtr$initialState = null;
		if (cb != null) {
			cb.accept(result);
		}
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);

		final DrawContext drawContext = getDrawContext(graphicsHolder);
		final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);

		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.profile.pick_block"),
				GRID_LEFT, 0,
				-1, false,
				GraphicsHolder.getDefaultLight());

		// 网格背景
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(
				GRID_LEFT - 2, GRID_TOP - 2,
				GRID_LEFT + COLS * CELL_SIZE + 1, GRID_TOP + VISIBLE_ROWS * CELL_SIZE + 1,
				0xFF101010);

		for (int i = 0; i < COLS * VISIBLE_ROWS; i++) {
			final int index = scrollOffset * COLS + i;
			if (index >= filteredBlocks.size()) {
				break;
			}
			final int col = i % COLS;
			final int row = i / COLS;
			final int x = GRID_LEFT + col * CELL_SIZE;
			final int y = GRID_TOP + row * CELL_SIZE;

			final Block block = filteredBlocks.get(index);
			final boolean isCurrent = currentBlock == block;
			final boolean isHovered = (x <= mouseX && mouseX < x + CELL_SIZE
					&& y <= mouseY && mouseY < y + CELL_SIZE);

			int bgColor;
			if (isCurrent) {
				bgColor = 0xFF305080;
			} else if (isHovered) {
				bgColor = 0xFF404040;
			} else {
				bgColor = 0xFF202020;
			}
			guiDrawing.drawRectangle(x, y, x + CELL_SIZE - 1, y + CELL_SIZE - 1, bgColor);
		}
		guiDrawing.finishDrawingRectangle();

		// 网格内方块图标
		if (drawContext != null) {
			for (int i = 0; i < COLS * VISIBLE_ROWS; i++) {
				final int index = scrollOffset * COLS + i;
				if (index >= filteredBlocks.size()) {
					break;
				}
				final int col = i % COLS;
				final int row = i / COLS;
				final int x = GRID_LEFT + col * CELL_SIZE;
				final int y = GRID_TOP + row * CELL_SIZE;
				final Block block = filteredBlocks.get(index);
				if (block != Blocks.AIR) {
					drawContext.drawItem(new ItemStack(block), x + 3, y + 3);
				} else {
					// 空气方块画个蓝色方块图标
					guiDrawing.beginDrawingRectangle();
					guiDrawing.drawRectangle(x + 5, y + 5, x + CELL_SIZE - 6, y + CELL_SIZE - 6, 0xFF3D6FB8);
					guiDrawing.finishDrawingRectangle();
				}
			}
		}

		// 滚动条
		final int totalRows = (filteredBlocks.size() + COLS - 1) / COLS;
		if (totalRows > VISIBLE_ROWS) {
			final int trackTop = GRID_TOP;
			final int trackHeight = VISIBLE_ROWS * CELL_SIZE;
			final int barHeight = Math.max(10, trackHeight * VISIBLE_ROWS / totalRows);
			final int maxOffset = totalRows - VISIBLE_ROWS;
			final int barY = trackTop + (maxOffset > 0 ? (int) ((long) (trackHeight - barHeight) * scrollOffset / maxOffset) : 0);
			guiDrawing.beginDrawingRectangle();
			guiDrawing.drawRectangle(
					GRID_LEFT + COLS * CELL_SIZE + 2, trackTop,
					GRID_LEFT + COLS * CELL_SIZE + 4, trackTop + trackHeight,
					0xFF202020);
			guiDrawing.drawRectangle(
					GRID_LEFT + COLS * CELL_SIZE + 2, barY,
					GRID_LEFT + COLS * CELL_SIZE + 4, barY + barHeight,
					0xFF808080);
			guiDrawing.finishDrawingRectangle();
		}

		// 右侧编辑面板背景
		final int rightWidth = Math.max(140, width - RIGHT_PANEL_X - 10);
		guiDrawing.beginDrawingRectangle();
		guiDrawing.drawRectangle(
				RIGHT_PANEL_X - 4, GRID_TOP - 2,
				RIGHT_PANEL_X + rightWidth + 4, GRID_TOP + VISIBLE_ROWS * CELL_SIZE + 1,
				0xFF181818);
		guiDrawing.finishDrawingRectangle();

		// 3D 预览:用 BlockRenderManager.renderBlockAsItem 渲染当前 BlockState
		if (drawContext != null && currentBlockState != null) {
			renderBlockPreview(drawContext, currentBlockState, PREVIEW_X, PREVIEW_Y, PREVIEW_SIZE);
		}

		// 方块名 + ID
		if (currentBlock != null) {
			graphicsHolder.drawText(
					TextHelper.literal(currentBlock.getName().getString()),
					RIGHT_PANEL_X, PREVIEW_Y,
					-1, false,
					GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText(
					TextHelper.literal(Registries.BLOCK.getId(currentBlock).toString()),
					RIGHT_PANEL_X, PREVIEW_Y + 12,
					0xFF808080, false,
					GraphicsHolder.getDefaultLight());
		}

		// 属性列表
		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.profile.properties"),
				RIGHT_PANEL_X, PROPS_TOP - 4,
				0xFFFFFF, false,
				GraphicsHolder.getDefaultLight());

		if (currentBlockState != null) {
			final List<Property<?>> props = new ArrayList<>(currentBlockState.getProperties());
			guiDrawing.beginDrawingRectangle();
			for (int i = 0; i < VISIBLE_PROPS; i++) {
				final int propIndex = propsScroll + i;
				if (propIndex >= props.size()) {
					break;
				}
				final int y = PROPS_TOP + i * PROPS_ROW_H;
				final boolean isHovered = (mouseY >= y && mouseY < y + PROPS_ROW_H
						&& mouseX >= RIGHT_PANEL_X && mouseX < RIGHT_PANEL_X + rightWidth);
				guiDrawing.drawRectangle(
						RIGHT_PANEL_X, y,
						RIGHT_PANEL_X + rightWidth, y + PROPS_ROW_H - 1,
						isHovered ? 0xFF383838 : 0xFF222222);
			}
			guiDrawing.finishDrawingRectangle();

			for (int i = 0; i < VISIBLE_PROPS; i++) {
				final int propIndex = propsScroll + i;
				if (propIndex >= props.size()) {
					break;
				}
				final Property<?> prop = props.get(propIndex);
				final int y = PROPS_TOP + i * PROPS_ROW_H;
				graphicsHolder.drawText(
						TextHelper.literal(prop.getName() + ": " + currentBlockState.get(prop)),
						RIGHT_PANEL_X + 4, y + 5,
						-1, false,
						GraphicsHolder.getDefaultLight());
			}
		}

		// 提示
		graphicsHolder.drawText(
				TextHelper.translatable("gui.s1mtr.profile.edit_hint"),
				RIGHT_PANEL_X, height - 36,
				0xFF808080, false,
				GraphicsHolder.getDefaultLight());

		// 悬停方块的 tooltip
		if (drawContext != null) {
			final Block hovered = getHoveredBlock(mouseX, mouseY);
			if (hovered != null) {
				final List<Text> tooltipLines = new ArrayList<>();
				if (hovered == Blocks.AIR) {
					tooltipLines.add(Text.literal("Air"));
					tooltipLines.add(Text.literal("minecraft:air").formatted(net.minecraft.util.Formatting.GRAY));
				} else {
					tooltipLines.add(hovered.getName());
					tooltipLines.add(Text.literal(Registries.BLOCK.getId(hovered).toString()).formatted(net.minecraft.util.Formatting.GRAY));
				}
				drawContext.drawTooltip(MinecraftClient.getInstance().textRenderer, tooltipLines, (int) mouseX, (int) mouseY);
			}
		}

		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	/**
	 * 用 BlockRenderManager.renderBlockAsItem 渲染 BlockState 的 3D 预览。
	 * 这样改变 facing/half 等属性时,预览会实时反映方块的实际朝向。
	 */
	private static void renderBlockPreview(DrawContext drawContext, BlockState state, int x, int y, int size) {
		if (state.getBlock() == Blocks.AIR) {
			drawContext.fill(x, y, x + size, y + size, 0xFF3D6FB8);
			return;
		}
		try {
			final MatrixStack matrices = drawContext.getMatrices();
			matrices.push();
			matrices.translate(x + size / 2.0, y + size / 2.0, 100);
			matrices.scale(size / 2.0f, -(size / 2.0f), size / 2.0f);
			final VertexConsumerProvider.Immediate immediate = drawContext.getVertexConsumers();
			final BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
			MinecraftClient.getInstance().getItemRenderer().renderItem(
					new ItemStack(state.getBlock()),
					ModelTransformationMode.GUI,
					false,
					matrices,
					immediate,
					15728880,
					OverlayTexture.DEFAULT_UV,
					model);
			immediate.draw();
			matrices.pop();
		} catch (Exception ignored) {
			drawContext.drawItem(new ItemStack(state.getBlock()), x, y);
		}
	}

	private Block getHoveredBlock(double mouseX, double mouseY) {
		if (mouseY < GRID_TOP || mouseY >= GRID_TOP + VISIBLE_ROWS * CELL_SIZE
				|| mouseX < GRID_LEFT || mouseX >= GRID_LEFT + COLS * CELL_SIZE) {
			return null;
		}
		final int col = (int) ((mouseX - GRID_LEFT) / CELL_SIZE);
		final int row = (int) ((mouseY - GRID_TOP) / CELL_SIZE);
		if (col < 0 || col >= COLS || row < 0 || row >= VISIBLE_ROWS) {
			return null;
		}
		final int index = scrollOffset * COLS + row * COLS + col;
		if (index < 0 || index >= filteredBlocks.size()) {
			return null;
		}
		return filteredBlocks.get(index);
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
}
