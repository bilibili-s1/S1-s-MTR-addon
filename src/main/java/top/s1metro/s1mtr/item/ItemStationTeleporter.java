package top.s1metro.s1mtr.item;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import top.s1metro.s1mtr.client.screen.StationListScreen;

/**
 * 车站传送器工具。
 * <p>
 * 玩家手持该物品右键方块时，弹出所有车站列表，选择任意一个车站传送过去。
 * 不限制在车站范围内使用——任何地方右键都能打开车站列表。
 */
public class ItemStationTeleporter extends Item {

	public ItemStationTeleporter(Settings settings) {
		super(settings.maxCount(1));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!context.getWorld().isClient()) {
			return ActionResult.PASS;
		}
		MinecraftClient.getInstance().setScreen(new StationListScreen());
		return ActionResult.SUCCESS;
	}
}
