package top.s1metro.s1mtr;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlock;
import top.s1metro.s1mtr.block.MultiDirectionNodeBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.s1metro.s1mtr.item.ItemAnteCompositeBuilder;
import top.s1metro.s1mtr.item.ItemFastTrackBuilder;
import top.s1metro.s1mtr.item.ItemNodeCopier;
import top.s1metro.s1mtr.item.ItemRailConnector;
import top.s1metro.s1mtr.item.ItemRailConnectorAuto;
import top.s1metro.s1mtr.item.ItemStationTeleporter;
import top.s1metro.s1mtr.network.PacketS1mtrBuildComposite;
import top.s1metro.s1mtr.network.PacketS1mtrConnectRails;
import top.s1metro.s1mtr.network.PacketS1mtrSaveAutoConnectorConfig;
import top.s1metro.s1mtr.network.PacketS1mtrSaveFastTrackConfig;
import top.s1metro.s1mtr.network.PacketS1mtrSaveNodeCopy;
import top.s1metro.s1mtr.network.PacketS1mtrSaveNodeCopierMode;
import top.s1metro.s1mtr.network.PacketS1mtrSetNodeDirection;
import top.s1metro.s1mtr.network.PacketS1mtrSetSidingAutoSpeed;
import top.s1metro.s1mtr.network.PacketS1mtrTeleport;
import top.s1metro.s1mtr.network.PacketS1mtrTeleportToStation;
import top.s1metro.s1mtr.service.S1mtrConfig;

public class S1mtraddon implements ModInitializer {
	public static final String MOD_ID = "s1mtraddon";

	/** MTR 自定义网络包注册中心。 */
	public static final org.mtr.mapping.registry.Registry REGISTRY = new org.mtr.mapping.registry.Registry();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 车站传送器 (直接继承原版 Item, 注册到原版 Registry)。 */
	public static final Item STATION_TELEPORTER = new ItemStationTeleporter(new Item.Settings());

	/** 轨道连接工具 (右键打开轨道网络图界面)。 */
	public static final Item RAIL_CONNECTOR = new ItemRailConnector(new Item.Settings());

	/** 快速建造轨道工具 (Shift+右键配置, 放副手+主手放节点自动连接并放样)。 */
	public static final Item FAST_TRACK_BUILDER = new ItemFastTrackBuilder(new Item.Settings());

	/** 自动速度轨道连接器 (点对点连接,自动选择推荐限速,复用 MTR 原版选点+预览)。 */
	public static final Item RAIL_CONNECTOR_AUTO = new ItemRailConnectorAuto();

	/** 轨道节点复制粘贴工具 (右键节点复制连接+属性, 右键空地放置新节点并自动重连)。 */
	public static final Item NODE_COPIER = new ItemNodeCopier(new Item.Settings());

	/** ANTE 复合构建器 (右键轨道节点打开放样编辑器,支持分层与通用预设)。装方速时禁用(方速有自带混合构建器)。 */
	public static Item ANTE_COMPOSITE_BUILDER;

	// ===== 万向节点（与方速 MTR4 运行时互斥）=====
	// 注意：不能在静态字段里无条件 new Block —— Minecraft 的 Block 构造会创建 intrusive holder，
	// 若方速存在而不注册这个 Block，fabric-registry-sync 启动校验会因「未注册的 holder」直接崩溃。
	// 因此改为在 onInitialize() 中按检测结果条件创建 + 注册。
	/** 万向节点方块：未绑定方向时旋转，可指定任意角度并用于轨道连接（摆脱 22.5° 离散限制）。 */
	public static MultiDirectionNodeBlock MULTI_DIRECTION_NODE_BLOCK;

	/** 万向节点方块实体类型。 */
	public static BlockEntityType<MultiDirectionNodeBlockEntity> MULTI_DIRECTION_NODE_BLOCK_ENTITY;

	/** 万向节点物品（放置用）。 */
	public static BlockItem MULTI_DIRECTION_NODE_ITEM;

	/** S1MTR mod 图标物品 (仅用于创造物品组标签页 icon, 不在物品列表中显示)。 */
	public static final Item S1MTR_ICON = new Item(new Item.Settings());

	/** S1MTR 物品组 (创造模式物品栏)。 */
	public static final ItemGroup S1MTR_ITEM_GROUP = FabricItemGroup.builder()
			.displayName(Text.translatable("itemGroup.s1mtraddon.main"))
			.icon(() -> new ItemStack(S1MTR_ICON))
			.entries((displayContext, entries) -> {
				entries.add(STATION_TELEPORTER);
				entries.add(RAIL_CONNECTOR);
				entries.add(RAIL_CONNECTOR_AUTO);
				entries.add(FAST_TRACK_BUILDER);
				entries.add(NODE_COPIER);
				if (ANTE_COMPOSITE_BUILDER != null) {
					entries.add(ANTE_COMPOSITE_BUILDER);
				}
				// 装了方速时由方速提供万向节点（功能更完整），不重复添加本模组的
				if (!top.s1metro.s1mtr.mixin.S1mtrMixinPlugin.isFangsuLoaded()) {
					entries.add(MULTI_DIRECTION_NODE_ITEM);
				}
			})
			.build();

	@Override
	public void onInitialize() {
		// 加载模组配置(写到 config/s1mtr/config.json,未安装 Mod Menu 也可手改)
		S1mtrConfig.load();

		REGISTRY.registerPacket(PacketS1mtrBuildComposite.class, PacketS1mtrBuildComposite::new);
		REGISTRY.registerPacket(PacketS1mtrConnectRails.class, PacketS1mtrConnectRails::new);
		REGISTRY.registerPacket(PacketS1mtrSaveFastTrackConfig.class, PacketS1mtrSaveFastTrackConfig::new);
		REGISTRY.registerPacket(PacketS1mtrSaveAutoConnectorConfig.class, PacketS1mtrSaveAutoConnectorConfig::new);
		REGISTRY.registerPacket(PacketS1mtrSaveNodeCopy.class, PacketS1mtrSaveNodeCopy::new);
		REGISTRY.registerPacket(PacketS1mtrSaveNodeCopierMode.class, PacketS1mtrSaveNodeCopierMode::new);
		REGISTRY.registerPacket(PacketS1mtrSetNodeDirection.class, PacketS1mtrSetNodeDirection::new);
		REGISTRY.registerPacket(PacketS1mtrSetSidingAutoSpeed.class, PacketS1mtrSetSidingAutoSpeed::new);
		REGISTRY.registerPacket(PacketS1mtrTeleport.class, PacketS1mtrTeleport::new);
		REGISTRY.registerPacket(PacketS1mtrTeleportToStation.class, PacketS1mtrTeleportToStation::new);
		REGISTRY.setupPackets(new org.mtr.mapping.holder.Identifier(MOD_ID, "packet"));

		// 注册物品到原版物品注册表
		net.minecraft.registry.Registry.register(Registries.ITEM, id("station_teleporter"),
				STATION_TELEPORTER);
		net.minecraft.registry.Registry.register(Registries.ITEM, id("rail_connector"),
				RAIL_CONNECTOR);
		net.minecraft.registry.Registry.register(Registries.ITEM, id("fast_track_builder"),
				FAST_TRACK_BUILDER);
		net.minecraft.registry.Registry.register(Registries.ITEM, id("rail_connector_auto"),
				RAIL_CONNECTOR_AUTO);
		net.minecraft.registry.Registry.register(Registries.ITEM, id("node_copier"),
				NODE_COPIER);
		if (!top.s1metro.s1mtr.mixin.S1mtrMixinPlugin.isFangsuLoaded()) {
			ANTE_COMPOSITE_BUILDER = new ItemAnteCompositeBuilder(new Item.Settings());
			net.minecraft.registry.Registry.register(Registries.ITEM, id("ante_composite_builder"),
					ANTE_COMPOSITE_BUILDER);
		}
		if (!top.s1metro.s1mtr.mixin.S1mtrMixinPlugin.isFangsuLoaded()) {
			// 仅在无方速时才创建并注册（Block 必须在Registry冻结前创建并注册）
			MULTI_DIRECTION_NODE_BLOCK = new MultiDirectionNodeBlock();
			MULTI_DIRECTION_NODE_BLOCK_ENTITY =
					BlockEntityType.Builder.create(MultiDirectionNodeBlockEntity::new, MULTI_DIRECTION_NODE_BLOCK).build(null);
			MULTI_DIRECTION_NODE_ITEM = new BlockItem(MULTI_DIRECTION_NODE_BLOCK, new Item.Settings());
			net.minecraft.registry.Registry.register(Registries.BLOCK, id("multi_direction_node"),
					MULTI_DIRECTION_NODE_BLOCK);
			net.minecraft.registry.Registry.register(Registries.BLOCK_ENTITY_TYPE, id("multi_direction_node"),
					MULTI_DIRECTION_NODE_BLOCK_ENTITY);
			net.minecraft.registry.Registry.register(Registries.ITEM, id("multi_direction_node"),
					MULTI_DIRECTION_NODE_ITEM);
		} else {
			LOGGER.info("[S1MTR] FangSu detected: using FangSu's multi-direction node, S1MTR's disabled.");
		}
		net.minecraft.registry.Registry.register(Registries.ITEM, id("s1mtr_icon"),
				S1MTR_ICON);

		// 注册物品组
		final RegistryKey<ItemGroup> groupKey = RegistryKey.of(RegistryKeys.ITEM_GROUP, id("main"));
		net.minecraft.registry.Registry.register(Registries.ITEM_GROUP, groupKey.getValue(), S1MTR_ITEM_GROUP);

		LOGGER.info("Hello from s1metro team!");
		LOGGER.info("S1 MTR Addon initializing...");
		LOGGER.info("MTR dependency detected - Ready to extend Minecraft Transit Railway!");

		// 注册跨 tick 的方块放置队列(分帧写入,削平放样时的 CPU 峰值)
		top.s1metro.s1mtr.service.PlacementQueue.register();
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}
}
