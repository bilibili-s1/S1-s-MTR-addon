package top.s1metro.s1mtr.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.s1metro.s1mtr.client.RailSpeedHelper;

import java.lang.reflect.Field;

@Mixin(PathData.class)
public abstract class PathDataMixin {

	@Inject(
			method = "<init>(Lorg/mtr/core/data/Rail;JJILorg/mtr/core/data/Position;Lorg/mtr/core/data/Position;)V",
			at = @At("TAIL")
	)
	private void s1mtr$modifyDwellTime(Rail rail, long savedRailBaseId, long dwellTime, int stopIndex, Position position1, Position position2, CallbackInfo ci) {
		if (rail == null || !RailSpeedHelper.isPlatform(rail)) {
			return;
		}
		long doorOpenDelay = RailSpeedHelper.getDoorOpenDelay(rail);
		long doorCloseDelay = RailSpeedHelper.getDoorCloseDelay(rail);
		if (doorOpenDelay <= 0 && doorCloseDelay <= 0) {
			return;
		}
		try {
			Field field = findDwellTimeField();
			if (field != null) {
				field.setAccessible(true);
				long currentDwellTime = field.getLong(this);
				field.setLong(this, currentDwellTime + doorOpenDelay);
			}
		} catch (Exception ignored) {
		}
	}

	private static Field findDwellTimeField() {
		Class<?> clazz = PathData.class;
		while (clazz != null) {
			try {
				return clazz.getDeclaredField("dwellTime");
			} catch (NoSuchFieldException e) {
				clazz = clazz.getSuperclass();
			}
		}
		return null;
	}
}