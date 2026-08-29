package top.s1metro.s1mtr.item;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.MinecraftClient;
import org.mtr.mapping.holder.ActionResult;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.CompoundTag;
import org.mtr.mapping.holder.ItemSettings;
import org.mtr.mapping.holder.ItemStack;
import org.mtr.mapping.holder.ItemUsageContext;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemNodeModifierBase;
import org.mtr.mod.packet.PacketUpdateData;
import top.s1metro.s1mtr.client.RailSpeedHelper;
import top.s1metro.s1mtr.client.screen.AutoConnectorConfigScreen;
import top.s1metro.s1mtr.client.builder.CompositeBuilder;
import top.s1metro.s1mtr.client.builder.CompositeLayerSchedule;
import top.s1metro.s1mtr.service.S1mtrConfig;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 自动速度轨道连接器。
 * <p>
 * 继承 MTR 的 {@link ItemNodeModifierBase}（isConnector=true），完整复用 MTR 原版轨道连接器的
 * 选点 + 预览线 + 防误触 + 朝向/方向校验逻辑：
 * <ul>
 *   <li>只有右键轨道节点才会进入选点流程（父类校验两节点 transportMode 一致）；</li>
 *   <li>第二点连接完成后父类自动清除已选点，避免"连完立即再次触发"的误触；</li>
 *   <li>两节点朝向不合法（如横节点连竖轨道）时 {@link #createAutoRail} 返回 null，父类提示
 *       "invalid orientation"，不会创建错误轨道。</li>
 * </ul>
 * <p>
 * 本类只实现抽象方法 {@link #onConnect}：用 MTR 算好的真实两端朝向构造临时轨道，据此计算
 * <b>含弯道曲率与坡度</b>的推荐限速（5km/h 级连续公式），再 clamp 到配置的自动最高速度，
 * 最后按该限速创建并保存轨道。
 */
public class ItemRailConnectorAuto extends ItemNodeModifierBase {

	/** 记录最后一次成功连接时的世界 tick，用于连接后的右键冷却，防止连完立刻误触重开选点。 */
	private static final String TAG_LAST_CONNECT = "s1mtr:lastConnectTick";
	/** 连接成功后的冷却 tick 数(0.5 秒)。期间在该节点上再次右键不会重新开始选点。 */
	private static final int CONNECT_COOLDOWN_TICKS = 10;

	/** 配置键:已选轨道样式(逗号分隔,可多选)。 */
	private static final String KEY_STYLES = "s1mtr_styles";
	/** 配置键:是否单向。 */
	private static final String KEY_ONE_WAY = "s1mtr_one_way";
	/** 配置键:连接后是否自动复合放样。 */
	private static final String KEY_AUTO_BUILD = "s1mtr_auto_build";
	/** 配置键:复合放样剖面(JSON)。 */
	public static final String KEY_SCHEDULE = "s1mtr_schedule";

	public ItemRailConnectorAuto() {
		// 前三个 flag 全 true：接受所有类型(普通/连续/飞机)的轨道节点；
		// 第四个 true = isConnector，让父类走 onConnect 连接逻辑而非 onRemove。
		super(true, true, true, true, new ItemSettings().maxCount(1));
	}

	/**
	 * 客户端 Shift+右键打开配置界面(轨道样式多选/单向/自动放样)；
	 * 普通右键走父类选点与预览逻辑。
	 */
	@Override
	public ActionResult useOnBlock2(ItemUsageContext context) {
		if (context.getWorld().isClient()) {
			final PlayerEntity player = context.getPlayer();
			if (player != null && player.isSneaking()) {
				MinecraftClient.getInstance().setScreen(
						new AutoConnectorConfigScreen(context.getStack().data));
				return ActionResult.SUCCESS;
			}
		}
		return super.useOnBlock2(context);
	}

	/**
	 * 第一点刚被父类写入 {@code pos} 后回调（服务端）。
	 * <p>
	 * 若距离上次成功连接过近（连完的瞬间用户手势还没松开又点到节点），立即撤销这次选点，
	 * 避免"刚连完又从该节点重新开始连接"的误触。正常情况委托父类写入 transport_mode。
	 */
	@Override
	protected void onStartClick(ItemUsageContext context, CompoundTag tag) {
		final org.mtr.mapping.holder.World world = context.getWorld();
		final long now = world.getTime();
		final long lastConnect = tag.getLong(TAG_LAST_CONNECT);
		if (now - lastConnect < CONNECT_COOLDOWN_TICKS) {
			tag.remove("pos");
			return;
		}
		super.onStartClick(context, tag);
	}

	/**
	 * 两个节点都确认后，由 MTR 父类 {@code onEndClick} 调用（服务端）。
	 * <p>
	 * {@code angle1/angle2} 是父类基于两节点真实朝向计算出的轨道端部朝向；
	 * 用它构造临时轨道即可拿到真实曲率，从而给出合理的弯道限速。
	 */
	@Override
	protected void onConnect(World world, ItemStack stack, TransportMode transportMode,
			BlockState state1, BlockState state2,
			BlockPos secondPos, BlockPos firstPos,
			Angle angle1, Angle angle2,
			ServerPlayerEntity player) {
		final ServerWorld serverWorld = ServerWorld.cast(world);

		final Position position1 = new Position(firstPos.getX(), firstPos.getY(), firstPos.getZ());
		final Position position2 = new Position(secondPos.getX(), secondPos.getY(), secondPos.getZ());

		// 从物品 NBT 读取配置:轨道样式(多选)/是否单向
		final Set<String> configuredStyles = getStyles(stack);
		final boolean oneWay = isOneWay(stack);

		final Rail rail = createAutoRail(position1, position2, angle1, angle2, transportMode,
				configuredStyles, oneWay);
		if (rail == null) {
			player.sendMessage(Text.cast(TextHelper.translatable("gui.s1mtr.auto_connector.invalid_orientation")), true);
			return;
		}

		// 记录连接时间，用于 onStartClick 的防误触冷却
		stack.getOrCreateTag().putLong(TAG_LAST_CONNECT, world.getTime());

		// 标记两端节点已连接，与 MTR 原版 ItemRailModifier.onConnect 一致
		markConnected(world, secondPos);
		markConnected(world, firstPos);

		PacketUpdateData.sendDirectlyToServerRail(serverWorld, rail);

		// 连接后若配置了复合剖面则自动放样(进入剖面编辑界面即视为启用)
		final CompositeLayerSchedule schedule = getSchedule(stack);
		if (schedule != null && schedule.size() > 0) {
			autoBuildProfile(serverWorld, rail, schedule);
		}
	}

	/** 连接器不使用移除逻辑，置空即可。 */
	@Override
	protected void onRemove(World world, BlockPos firstPos, BlockPos secondPos, ServerPlayerEntity player) {
	}

	/**
	 * 构造自动速度轨道：
	 * <ol>
	 *   <li>先用真实朝向构造临时轨道，用 {@link RailSpeedHelper#calculateRecommendedSpeed} 计算
	 *       含曲率/坡度的推荐限速；</li>
	 *   <li>把推荐限速 clamp 到配置的自动最高速度；</li>
	 *   <li>用该限速重新构造正式轨道并返回；朝向不合法或无法构成轨道时返回 null。</li>
	 * </ol>
	 */
	private Rail createAutoRail(Position position1, Position position2, Angle angle1, Angle angle2,
			TransportMode transportMode, Set<String> configuredStyles, boolean oneWay) {
		if (position1.equals(position2)) {
			return null;
		}

		// 用标准普通轨道类型(STONE)的参数构造,与 MTR 原版 ItemRailModifier.createRail 一致,
		// 确保 canAccelerate/hasSignal/railShape 与节点匹配,避免部分本可连接的轨道被判 invalid。
		// 注:Rail.newRail 最后一个布尔是 canConnectRemotely,原版普通轨道传 hasSignal(true)。
		final RailType baseType = RailType.STONE;
		final ObjectArrayList<String> probeStyles = new ObjectArrayList<>();

		// 1. 用真实朝向构造临时轨道，取得含曲率的推荐限速。
		//    注意:临时轨道仅用于计算推荐速度,即使 isValid 失败也不应导致连接失败
		//    (原版连接器对部分朝向组合也能连接),故失败时回退到配置上限。
		long recommended = -1;
		final Rail probe = Rail.newRail(
				position1, angle1, position2, angle2,
				baseType.railShape, 0, probeStyles,
				baseType.speedLimit, baseType.speedLimit, false, false,
				baseType.canAccelerate, false, true, transportMode);
		if (probe.isValid()) {
			recommended = RailSpeedHelper.calculateRecommendedSpeed(probe);
		}

		final int maxSpeed = S1mtrConfig.autoConnectorMaxSpeed();
		final long speed;
		if (recommended <= 0) {
			// 平坡/无弯道/无法计算：使用配置上限
			speed = maxSpeed;
		} else {
			speed = Math.min(recommended, maxSpeed);
		}

		// 2. 用最终限速重新构造正式轨道。样式取配置的多选集合(为空时回退 default)；
		//    单向时 speedLimit2 = 0。
		final ObjectArrayList<String> styles = new ObjectArrayList<>();
		if (configuredStyles != null && !configuredStyles.isEmpty()) {
			styles.addAll(configuredStyles);
		} else {
			styles.add("default");
		}
		final long speedLimit2 = oneWay ? 0 : speed;
		final Rail result = Rail.newRail(
				position1, angle1, position2, angle2,
				baseType.railShape, 0, styles,
				speed, speedLimit2, false, false,
				baseType.canAccelerate, false, true, transportMode);
		return result.isValid() ? result : null;
	}

	/** 按配置的复合剖面沿轨道放样。 */
	private static void autoBuildProfile(ServerWorld serverWorld, Rail rail, CompositeLayerSchedule schedule) {
		if (schedule == null || schedule.size() == 0) {
			return;
		}
		try {
			CompositeBuilder.build(serverWorld, rail, schedule);
		} catch (Exception ignored) {
		}
	}

	private static void markConnected(World world, BlockPos pos) {
		final BlockState state = world.getBlockState(pos);
		if (state != null && state.getBlock().data instanceof BlockNode) {
			world.setBlockState(pos, state.with(new Property(BlockNode.IS_CONNECTED.data), Boolean.TRUE), 3);
		}
	}

	// ===== 配置 NBT 读写 =====

	/** 已选轨道样式集合(可多选)。 */
	public static Set<String> getStyles(ItemStack stack) {
		final Set<String> set = new LinkedHashSet<>();
		final String raw = stack.getOrCreateTag().getString(KEY_STYLES);
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
			stack.getOrCreateTag().putString(KEY_STYLES, "");
		} else {
			stack.getOrCreateTag().putString(KEY_STYLES, String.join(",", styles));
		}
	}

	public static boolean isOneWay(ItemStack stack) {
		return stack.getOrCreateTag().getBoolean(KEY_ONE_WAY);
	}

	public static void setOneWay(ItemStack stack, boolean oneWay) {
		stack.getOrCreateTag().putBoolean(KEY_ONE_WAY, oneWay);
	}

	public static boolean isAutoBuild(ItemStack stack) {
		return stack.getOrCreateTag().getBoolean(KEY_AUTO_BUILD);
	}

	public static void setAutoBuild(ItemStack stack, boolean autoBuild) {
		stack.getOrCreateTag().putBoolean(KEY_AUTO_BUILD, autoBuild);
	}

	public static CompositeLayerSchedule getSchedule(ItemStack stack) {
		final String json = stack.getOrCreateTag().getString(KEY_SCHEDULE);
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
			stack.getOrCreateTag().putString(KEY_SCHEDULE, schedule.serialize());
		}
	}
}
