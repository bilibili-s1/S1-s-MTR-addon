package top.s1metro.s1mtr.common;

import org.mtr.core.data.Rail;
import top.s1metro.s1mtr.S1MtrAddon;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RailSettings {
    public static final String DOOR_OPEN_DELAY_PREFIX = "s1mtr:doorOpenDelay=";
    public static final String DOOR_CLOSE_DELAY_PREFIX = "s1mtr:doorCloseDelay=";

    private static final AtomicBoolean CONSTRUCTOR_WARNING_LOGGED = new AtomicBoolean();

    private RailSettings() {
    }

    public static Rail copyWithCustomParams(Rail original, long speed, long doorOpenDelaySeconds, long doorCloseDelaySeconds) {
        final List<String> styles = getStyles(original);
        styles.removeIf(style -> style.startsWith(DOOR_OPEN_DELAY_PREFIX) || style.startsWith(DOOR_CLOSE_DELAY_PREFIX));
        if (doorOpenDelaySeconds > 0) styles.add(DOOR_OPEN_DELAY_PREFIX + doorOpenDelaySeconds);
        if (doorCloseDelaySeconds > 0) styles.add(DOOR_CLOSE_DELAY_PREFIX + doorCloseDelaySeconds);

        try {
            final Constructor<?> constructor = findCanonicalConstructor(original.getClass());
            final Class<?>[] parameterTypes = constructor.getParameterTypes();
            final Object mutableStyles = createStyleList(parameterTypes[18], styles);
            final long originalSpeed1 = getSpeedLimit1(original);
            final long normalizedSpeed = Math.max(1, speed);
            final Object[] arguments = {
                    ReflectionUtil.getFieldValue(original, "position1"),
                    ReflectionUtil.getFieldValue(original, "angle1"),
                    ReflectionUtil.getFieldValue(original, "position2"),
                    ReflectionUtil.getFieldValue(original, "angle2"),
                    ReflectionUtil.getFieldValue(original, "shape"),
                    ReflectionUtil.getFieldValue(original, "verticalRadius"),
                    ReflectionUtil.getFieldValue(original, "tiltPoints"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDegrees1"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDistance1a"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDegrees1a"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDegrees1b"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDistance1b"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDegreesMiddle"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDistance2b"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDegrees2b"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDegrees2a"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDistance2a"),
                    ReflectionUtil.getFieldValue(original, "tiltAngleDegrees2"),
                    mutableStyles,
                    originalSpeed1 == 0 ? 0L : normalizedSpeed,
                    normalizedSpeed,
                    ReflectionUtil.getFieldValue(original, "isPlatform"),
                    ReflectionUtil.getFieldValue(original, "isSiding"),
                    ReflectionUtil.getFieldValue(original, "canAccelerate"),
                    ReflectionUtil.getFieldValue(original, "canTurnBack"),
                    ReflectionUtil.getFieldValue(original, "canConnectRemotely"),
                    ReflectionUtil.getFieldValue(original, "canHaveSignal"),
                    ReflectionUtil.getFieldValue(original, "transportMode")
            };
            constructor.setAccessible(true);
            final Rail result = (Rail) constructor.newInstance(arguments);
            copySignalColors(result, original);
            S1MtrAddon.LOGGER.info("Rebuilt MTR Rail: original={}, updated={}, speed1={}, speed2={}, styles={}",
                    describeRail(original), describeRail(result), getSpeedLimit1(result), getSpeedLimit2(result), getStyles(result));
            return result;
        } catch (Throwable throwable) {
            if (CONSTRUCTOR_WARNING_LOGGED.compareAndSet(false, true)) {
                S1MtrAddon.LOGGER.error("Unable to use the MTR 4.1 canonical Rail constructor; falling back to style-only copy", throwable);
            }
            return copyStylesOnly(original, styles);
        }
    }

    private static Constructor<?> findCanonicalConstructor(Class<?> railClass) throws NoSuchMethodException {
        for (Constructor<?> constructor : railClass.getDeclaredConstructors()) {
            final Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 28
                    && types[5] == double.class
                    && types[6] == long.class
                    && types[19] == long.class
                    && types[20] == long.class
                    && types[21] == boolean.class
                    && types[27].getSimpleName().equals("TransportMode")) {
                return constructor;
            }
        }
        throw new NoSuchMethodException("No 28-argument MTR 4.1 Rail constructor on " + railClass.getName());
    }

    private static Rail copyStylesOnly(Rail original, List<String> styles) {
        try {
            for (Method method : original.getClass().getDeclaredMethods()) {
                final Class<?>[] types = method.getParameterTypes();
                if (Modifier.isStatic(method.getModifiers()) && method.getName().equals("copy")
                        && types.length == 2 && types[0].isAssignableFrom(original.getClass())) {
                    final Object styleList = createStyleList(types[1], styles);
                    method.setAccessible(true);
                    return (Rail) method.invoke(null, original, styleList);
                }
            }
        } catch (Throwable throwable) {
            S1MtrAddon.LOGGER.error("Unable to copy MTR rail styles", throwable);
        }
        return original;
    }

    private static Object createStyleList(Class<?> listType, List<String> styles) throws ReflectiveOperationException {
        final Constructor<?> constructor = listType.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object list = constructor.newInstance();
        Method addMethod = null;
        for (Method method : listType.getMethods()) {
            if (method.getName().equals("add") && method.getParameterCount() == 1) {
                addMethod = method;
                break;
            }
        }
        if (addMethod == null) throw new NoSuchMethodException(listType.getName() + ".add");
        for (String style : styles) addMethod.invoke(list, style);
        return list;
    }

    public static List<String> getStyles(Rail rail) {
        final List<String> styles = new ArrayList<>();
        try {
            final Method method = rail.getClass().getMethod("getStyles");
            final Object value = method.invoke(rail);
            if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) if (item != null) styles.add(item.toString());
            }
        } catch (Throwable throwable) {
            try {
                final Object value = ReflectionUtil.getFieldValue(rail, "styles");
                if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) if (item != null) styles.add(item.toString());
                }
            } catch (Throwable ignored) {
            }
        }
        return styles;
    }


    public static long getPreferredSpeedLimit(Rail rail) {
        long speed = invokeLong(rail, "getSpeedLimitKilometersPerHour", false, -1);
        if (speed <= 0) speed = invokeLong(rail, "getSpeedLimitKilometersPerHour", true, -1);
        if (speed <= 0) speed = getSpeedLimit2(rail);
        if (speed <= 0) speed = getSpeedLimit1(rail);
        return speed <= 0 ? 80 : speed;
    }

    public static String describeRail(Rail rail) {
        if (rail == null) return "null";
        String id = "unknown";
        try {
            final Object value = rail.getClass().getMethod("getHexId").invoke(rail);
            if (value != null) id = value.toString();
        } catch (Throwable ignored) {
        }
        return rail.getClass().getName() + "[id=" + id + ",speed1=" + getSpeedLimit1(rail)
                + ",speed2=" + getSpeedLimit2(rail) + ",styles=" + getStyles(rail) + ']';
    }

    private static void copySignalColors(Rail target, Rail source) {
        try {
            final Method method = target.getClass().getMethod("copySignalColors", source.getClass());
            method.invoke(target, source);
        } catch (NoSuchMethodException ignored) {
            // Older core builds do not expose this runtime-state helper.
        } catch (Throwable throwable) {
            S1MtrAddon.LOGGER.warn("Unable to preserve MTR rail signal colors", throwable);
        }
    }

    public static long getSpeedLimit1(Rail rail) {
        return ReflectionUtil.getLong(rail, "speedLimit1", invokeLong(rail, "getSpeedLimitKilometersPerHour", false, 80));
    }

    public static long getSpeedLimit2(Rail rail) {
        return ReflectionUtil.getLong(rail, "speedLimit2", invokeLong(rail, "getSpeedLimitKilometersPerHour", true, 80));
    }

    private static long invokeLong(Object owner, String methodName, boolean argument, long fallback) {
        try {
            return ((Number) owner.getClass().getMethod(methodName, boolean.class).invoke(owner, argument)).longValue();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static boolean isPlatform(Rail rail) {
        return invokeBoolean(rail, "isPlatform", ReflectionUtil.getBoolean(rail, "isPlatform", false));
    }

    public static boolean isSiding(Rail rail) {
        return invokeBoolean(rail, "isSiding", ReflectionUtil.getBoolean(rail, "isSiding", false));
    }

    public static boolean canTurnBack(Rail rail) {
        return invokeBoolean(rail, "canTurnBack", ReflectionUtil.getBoolean(rail, "canTurnBack", false));
    }

    private static boolean invokeBoolean(Object owner, String methodName, boolean fallback) {
        try {
            return (Boolean) owner.getClass().getMethod(methodName).invoke(owner);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static long getDoorOpenDelayMillis(Rail rail) {
        return readDelaySeconds(rail, DOOR_OPEN_DELAY_PREFIX) * 1000;
    }

    public static long getDoorCloseDelayMillis(Rail rail) {
        return readDelaySeconds(rail, DOOR_CLOSE_DELAY_PREFIX) * 1000;
    }

    private static long readDelaySeconds(Rail rail, String prefix) {
        for (String style : getStyles(rail)) {
            if (style.startsWith(prefix)) {
                try {
                    return Math.max(0, Long.parseLong(style.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
