package top.s1metro.s1mtr.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import top.s1metro.s1mtr.client.S1mtrClientProxy;

/**
 * 车站传送器工具。
 * <p>
 * 玩家手持该物品右键方块时，弹出所有车站列表，选择任意一个车站传送过去。
 * 不限制在车站范围内使用——任何地方右键都能打开车站列表。
 * <p>
 * 注意：打开界面的逻辑通过反射委托给客户端专用的 {@code ClientScreenOpener}，
 * 以避免本类（服务端也会加载）在链接期依赖 client.screen 包下的 Screen 子类。
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
		S1mtrClientProxy.openScreen("openStationList");
		return ActionResult.SUCCESS;
	}
}
