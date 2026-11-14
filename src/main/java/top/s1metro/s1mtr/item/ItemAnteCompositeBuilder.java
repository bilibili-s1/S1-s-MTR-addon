package top.s1metro.s1mtr.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import top.s1metro.s1mtr.client.S1mtrClientProxy;

/**
 * ANTE 复合构建器。
 * <p>
 * 移植自 FangSu 模组的"混合构建器"(ANTE 版),并与本模组原有的复合构建器融合:
 * <ul>
 *   <li>沿轨道曲线放样横截面(与原有复合构建器同一套放样算法);</li>
 *   <li>支持分层(每一层一个剖面并可循环放样),复用原有的分层调度表;</li>
 *   <li>预设与原有复合构建器通用(同一套预设存储,可互相读取);</li>
 *   <li>界面使用 ANTE 风格的方块预览(立体方块),不包含原模组的"混合模式"。</li>
 * </ul>
 * 玩家手持该物品右键轨道节点即可打开编辑器。
 */
public class ItemAnteCompositeBuilder extends Item {

	public ItemAnteCompositeBuilder(Settings settings) {
		super(settings.maxCount(1));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!context.getWorld().isClient()) {
			return ActionResult.PASS;
		}
		S1mtrClientProxy.openAnteCompositeBuilder(context.getBlockPos());
		return ActionResult.SUCCESS;
	}
}
