package top.s1metro.s1mtr.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import top.s1metro.s1mtr.client.S1mtrClientProxy;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;

import java.util.Set;

/**
 * 快速建造轨道工具。
 * <p>
 * 手持该物品 Shift+右键打开配置界面，可设置轨道限速、样式与复合构建剖面。
 * 把配置好的物品放入副手即视为"开始"，此时玩家用主手放置轨道节点方块，
 * 会自动从上一个节点连接轨道并进行复合放样。
 * <p>
 * 配置内容 (速度/样式/剖面) 持久保存在该物品自身的 NBT 中，副手放不同配置的物品效果不同。
 */
public class ItemFastTrackBuilder extends Item {

	public static final String KEY_SPEED = "s1mtr_speed";
	public static final String KEY_STYLES = "s1mtr_styles";
	public static final String KEY_SCHEDULE = "s1mtr_schedule";
	public static final String KEY_PREV_X = "s1mtr_prev_x";
	public static final String KEY_PREV_Y = "s1mtr_prev_y";
	public static final String KEY_PREV_Z = "s1mtr_prev_z";
	public static final String KEY_SESSION_STARTED = "s1mtr_session_started";

	public ItemFastTrackBuilder(Settings settings) {
		super(settings.maxCount(1));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!context.getWorld().isClient()) {
			return ActionResult.PASS;
		}
		final ItemStack stack = context.getStack();
		final PlayerEntity player = context.getPlayer();
		if (player == null) {
			return ActionResult.PASS;
		}
		// Shift+右键打开配置界面
		if (player.isSneaking()) {
			S1mtrClientProxy.openFastTrackConfig(stack);
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	/**
	 * 每 tick 调用，用于在工具被放入副手时开启新会话（重置上一个节点），
	 * 移出副手时结束会话。仅在服务端处理。
	 */
	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient() || !(entity instanceof PlayerEntity player)) {
			return;
		}
		final boolean inOffHand = player.getOffHandStack() == stack;
		final NbtCompound nbt = stack.getOrCreateNbt();
		if (inOffHand) {
			// 刚从非副手放入副手时，重置上一个节点，开启新会话
			if (!nbt.getBoolean(KEY_SESSION_STARTED)) {
				nbt.putBoolean(KEY_SESSION_STARTED, true);
				nbt.remove(KEY_PREV_X);
				nbt.remove(KEY_PREV_Y);
				nbt.remove(KEY_PREV_Z);
			}
		} else {
			// 不在副手，结束会话
			nbt.putBoolean(KEY_SESSION_STARTED, false);
		}
	}

	// ===== NBT 读取/写入工具方法 =====

	public static long getSpeed(ItemStack stack) {
		return stack.getOrCreateNbt().getLong(KEY_SPEED);
	}

	public static void setSpeed(ItemStack stack, long speed) {
		stack.getOrCreateNbt().putLong(KEY_SPEED, Math.max(1, speed));
	}

	/** 已选轨道样式集合(可多选)。 */
	public static Set<String> getStyles(ItemStack stack) {
		final Set<String> set = new java.util.LinkedHashSet<>();
		final String raw = stack.getOrCreateNbt().getString(KEY_STYLES);
		if (!raw.isEmpty()) {
			for (String s : raw.split(",")) {
				if (!s.isEmpty()) {
					set.add(s);
				}
			}
		}
		return set;
	}

	public static void setStyles(ItemStack stack, Set<String> styles) {
		if (styles == null || styles.isEmpty()) {
			stack.getOrCreateNbt().putString(KEY_STYLES, "");
		} else {
			stack.getOrCreateNbt().putString(KEY_STYLES, String.join(",", styles));
		}
	}

	public static CompositeLayerSchedule getSchedule(ItemStack stack) {
		final String json = stack.getOrCreateNbt().getString(KEY_SCHEDULE);
		if (json.isEmpty()) {
			return new CompositeLayerSchedule();
		}
		try {
			return CompositeLayerSchedule.deserialize(json);
		} catch (Exception e) {
			return new CompositeLayerSchedule();
		}
	}

	public static void setSchedule(ItemStack stack, CompositeLayerSchedule schedule) {
		if (schedule != null) {
			stack.getOrCreateNbt().putString(KEY_SCHEDULE, schedule.serialize());
		}
	}

	public static boolean hasPreviousNode(ItemStack stack) {
		return stack.getOrCreateNbt().contains(KEY_PREV_X);
	}

	public static long[] getPreviousNode(ItemStack stack) {
		final NbtCompound nbt = stack.getOrCreateNbt();
		return new long[]{nbt.getLong(KEY_PREV_X), nbt.getLong(KEY_PREV_Y), nbt.getLong(KEY_PREV_Z)};
	}

	public static void setPreviousNode(ItemStack stack, long x, long y, long z) {
		final NbtCompound nbt = stack.getOrCreateNbt();
		nbt.putLong(KEY_PREV_X, x);
		nbt.putLong(KEY_PREV_Y, y);
		nbt.putLong(KEY_PREV_Z, z);
	}

	public static void clearPreviousNode(ItemStack stack) {
		final NbtCompound nbt = stack.getOrCreateNbt();
		nbt.remove(KEY_PREV_X);
		nbt.remove(KEY_PREV_Y);
		nbt.remove(KEY_PREV_Z);
	}
}
