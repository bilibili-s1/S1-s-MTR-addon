package top.s1metro.s1mtr.mixin;

import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 万向节点精确角度的列车渲染修复：客户端收包预填路径缓存时，用真实客户端数据替换 22.5° 快照轨道。
 * <p>
 * 本 mixin 属于万向节点功能组，装了方速（自带同名修复）时由 {@link S1mtrMixinPlugin} 跳过；
 * 与本模组 dwellTime 修复（{@link PathDataMixin}）分开，避免共存时误伤其它功能。
 *
 * <p>移植自 FangSu For MTR 4（MIT）。
 */
@Mixin(value = PathData.class, remap = false)
public abstract class AnglePathDataMixin {

	@Shadow(remap = false)
	private Rail rail;

	@Shadow(remap = false)
	public abstract Position getOrderedPosition1();

	@Shadow(remap = false)
	public abstract Position getOrderedPosition2();

	@Inject(method = "writePathCache(Lorg/mtr/core/data/Data;)V", at = @At("HEAD"), remap = false, cancellable = true)
	private void s1mtr$writePathCache(Data data, CallbackInfo ci) {
		if (data.positionsToRail.isEmpty()) {
			final Data realData = s1mtr$getClientData();
			if (realData != null && realData != data) {
				final Rail realRail = Data.tryGet(realData.positionsToRail, getOrderedPosition1(), getOrderedPosition2());
				if (realRail != null) {
					rail = realRail;
					ci.cancel();
				}
			}
		}
	}

	private static Data s1mtr$getClientData() {
		try {
			return org.mtr.mod.client.MinecraftClientData.getInstance();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
