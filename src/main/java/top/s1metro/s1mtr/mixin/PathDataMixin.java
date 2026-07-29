package top.s1metro.s1mtr.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.s1metro.s1mtr.S1MtrAddon;
import top.s1metro.s1mtr.common.RailSettings;
import top.s1metro.s1mtr.common.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(PathData.class)
public abstract class PathDataMixin {
    private static volatile Field s1mtr$dwellTimeField;
    private static final AtomicBoolean s1mtr$warningLogged = new AtomicBoolean();

    @Inject(
            method = "<init>(Lorg/mtr/core/data/Rail;JJILorg/mtr/core/data/Position;Lorg/mtr/core/data/Position;)V",
            at = @At("TAIL"),
            require = 0
    )
    private void s1mtr$addDoorOpenDelayToDwellTime(
            Rail rail, long savedRailBaseId, long dwellTime, int stopIndex,
            Position position1, Position position2, CallbackInfo callbackInfo
    ) {
        if (rail == null || !RailSettings.isPlatform(rail)) return;
        final long openDelay = RailSettings.getDoorOpenDelayMillis(rail);
        if (openDelay <= 0) return;

        try {
            Field field = s1mtr$dwellTimeField;
            if (field == null) {
                field = ReflectionUtil.findField(PathData.class, "dwellTime");
                s1mtr$dwellTimeField = field;
            }
            if (field != null) {
                field.setLong(this, field.getLong(this) + openDelay);
            }
        } catch (ReflectiveOperationException exception) {
            if (s1mtr$warningLogged.compareAndSet(false, true)) {
                S1MtrAddon.LOGGER.error("Unable to extend MTR platform dwell time", exception);
            }
        }
    }
}
