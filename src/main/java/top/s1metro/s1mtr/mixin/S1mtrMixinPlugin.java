package top.s1metro.s1mtr.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 互斥插件：与方速模组（fangsu，MTR4 版）共存时禁用本模组那一套万向节点 mixin。
 * <p>
 * 原因：方速自带同名目标的 {@code AngleMixin / EnumHelperMixin / SidingPathFinderMixin /
 * ItemNodeModifierBaseMixin / ItemRailModifierAccessorMixin / BlockNodeMixin / PathDataMixin}，
 * 其中 {@code EnumHelperMixin} 用 {@code @Overwrite} 覆写 {@code EnumHelper.valueOf}，
 * 两个模组同时覆写会直接崩溃；另外两套独立的 {@code PHANTOM_CACHE} 会生成不同的 Angle 实例，
 * 破坏 MTR core 里基于 {@code ==} 的角度比较（寻路、倾斜传播）。
 * <p>
 * 因此：装了方速时，本模组的万向节点方块也不再注册（避免与方速的 {@code fangsu:multi_direction_node}
 * 功能重复且无法连接），全部交给方速的更完整实现；未装方速时才启用本模组的最小版。
 * 与本模组其它功能（侧线限速、晚点修正、复合构建器等）相关的 mixin 不受影响。
 */
public class S1mtrMixinPlugin implements IMixinConfigPlugin {

	/** 方速 MTR4 版的 mod id。 */
	public static final String FANGSU_MOD_ID = "fangsu";

	private static Boolean fangsuLoaded;

	/** 全局判定：供注册表在初始化时决定是否注册万向节点方块。 */
	public static boolean isFangsuLoaded() {
		if (fangsuLoaded == null) {
			try {
				fangsuLoaded = FabricLoader.getInstance().isModLoaded(FANGSU_MOD_ID);
			} catch (Throwable ignored) {
				fangsuLoaded = false;
			}
		}
		return fangsuLoaded;
	}

	/** 方速存在时需要跳过的 mixin 简单类名（与本模组万向节点功能相关）。 */
	private static final Set<String> FANGSU_CONFLICTS = Set.of(
			"AngleMixin",
			"EnumHelperMixin",
			"SidingPathFinderMixin",
			"ItemRailModifierAccessorMixin",
			"ItemNodeModifierBaseMixin",
			"BlockNodeMixin",
			"AnglePathDataMixin"
	);

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (!isFangsuLoaded()) {
			return true;
		}
		final String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		return !FANGSU_CONFLICTS.contains(simpleName);
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
						 String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
						  String mixinClassName, IMixinInfo mixinInfo) {
	}
}
