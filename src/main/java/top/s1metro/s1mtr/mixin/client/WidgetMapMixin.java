package top.s1metro.s1mtr.mixin.client;

import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.screen.WidgetMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.s1metro.s1mtr.client.screen.StationRightClickMenuScreen;

import java.lang.reflect.Method;

/**
 * Mixin 到 MTR 的 WidgetMap（铁路仪表板地图面板）。
 * <p>
 * 在地图上右键时检测鼠标位置是否在某车站 x-z 范围内，
 * 若是则弹出右键菜单（传送/编辑），并取消原 mouseClicked2 的逻辑。
 */
@Mixin(WidgetMap.class)
public class WidgetMapMixin {

	@Inject(method = "mouseClicked2", at = @At("HEAD"), cancellable = true)
	private void s1mtr$onRightClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (button != 1) {
			return;
		}

		final WidgetMap self = (WidgetMap) (Object) this;

		// 用反射调用 private coordsToWorldPos(double, double) 获取世界坐标
		DoubleDoubleImmutablePair worldPos = null;
		try {
			final Method m = WidgetMap.class.getDeclaredMethod("coordsToWorldPos", double.class, double.class);
			m.setAccessible(true);
			worldPos = (DoubleDoubleImmutablePair) m.invoke(self, mouseX, mouseY);
		} catch (Exception e) {
			return;
		}
		if (worldPos == null) {
			return;
		}

		final double worldX = worldPos.leftDouble();
		final double worldZ = worldPos.rightDouble();

		// 遍历车站，检查 (worldX, worldZ) 是否在 x-z 范围内
		Station foundStation = null;
		for (Station station : MinecraftClientData.getInstance().stations) {
			if (worldX >= station.getMinX() && worldX <= station.getMaxX()
					&& worldZ >= station.getMinZ() && worldZ <= station.getMaxZ()) {
				foundStation = station;
				break;
			}
		}

		if (foundStation != null) {
			final Position center = foundStation.getCenter();
			MinecraftClient.getInstance().setScreen(
					new StationRightClickMenuScreen(foundStation, center));
			cir.setReturnValue(true);
		}
	}
}
