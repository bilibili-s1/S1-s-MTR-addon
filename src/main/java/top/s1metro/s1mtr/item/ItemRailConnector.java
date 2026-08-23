package top.s1metro.s1mtr.item;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import top.s1metro.s1mtr.client.screen.RailNetworkMapScreen;

/**
 * 轨道连接工具。
 * <p>
 * 玩家手持该物品右键时，打开"轨道网络图"界面。界面上显示存档内所有轨道与轨道节点，
 * 依次点击两个节点后可输入限速并自动创建双向轨道连接。
 */
public class ItemRailConnector extends Item {

	public ItemRailConnector(Settings settings) {
		super(settings.maxCount(1));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!context.getWorld().isClient()) {
			return ActionResult.PASS;
		}
		MinecraftClient.getInstance().setScreen(new RailNetworkMapScreen());
		return ActionResult.SUCCESS;
	}
}
