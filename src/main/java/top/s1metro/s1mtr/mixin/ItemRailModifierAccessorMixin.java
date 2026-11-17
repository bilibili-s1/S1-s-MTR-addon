package top.s1metro.s1mtr.mixin;

import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemRailModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露轨道连接器的类型与单向标志，供万向节点建轨时按连接器类型生成轨道。 */
@Mixin(value = ItemRailModifier.class, remap = false)
public interface ItemRailModifierAccessorMixin {

	@Accessor(value = "railType", remap = false)
	RailType getRailType();

	@Accessor(value = "isOneWay", remap = false)
	boolean isOneWay();
}
