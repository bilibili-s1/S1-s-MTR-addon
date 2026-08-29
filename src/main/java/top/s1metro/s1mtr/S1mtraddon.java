package top.s1metro.s1mtr;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
