package top.s1metro.s1mtr.common;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class ReflectionUtil {
    private ReflectionUtil() {
    }

    public static Field findField(Class<?> startClass, String fieldName) {
        Class<?> type = startClass;
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    public static Object getFieldValue(Object owner, String fieldName) throws ReflectiveOperationException {
        final Field field = findField(owner.getClass(), fieldName);
        if (field == null) {
            throw new NoSuchFieldException(owner.getClass().getName() + '.' + fieldName);
        }
        return field.get(owner);
    }

    public static long getLong(Object owner, String fieldName, long fallback) {
        try {
            final Object value = getFieldValue(owner, fieldName);
            return value instanceof Number number ? number.longValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static double getDouble(Object owner, String fieldName, double fallback) {
        try {
            final Object value = getFieldValue(owner, fieldName);
            return value instanceof Number number ? number.doubleValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static boolean getBoolean(Object owner, String fieldName, boolean fallback) {
        try {
            final Object value = getFieldValue(owner, fieldName);
            return value instanceof Boolean bool ? bool : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static <T> T findFirstValueDeep(Object owner, Class<T> valueType, int maxDepth) {
        final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return findFirstValueDeep(owner, valueType, Math.max(0, maxDepth), visited);
    }

    private static <T> T findFirstValueDeep(Object owner, Class<T> valueType, int depth, Set<Object> visited) {
        if (owner == null || !visited.add(owner)) return null;
        if (valueType.isInstance(owner)) return valueType.cast(owner);
        if (depth <= 0) return null;

        if (owner instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                final T found = findFirstValueDeep(value, valueType, depth - 1, visited);
                if (found != null) return found;
            }
            return null;
        }
        if (owner instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                final T found = findFirstValueDeep(value, valueType, depth - 1, visited);
                if (found != null) return found;
            }
            return null;
        }
        if (owner.getClass().isArray()) {
            final int length = Array.getLength(owner);
            for (int index = 0; index < length; index++) {
                final T found = findFirstValueDeep(Array.get(owner, index), valueType, depth - 1, visited);
                if (found != null) return found;
            }
            return null;
        }

        Class<?> type = owner.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                try {
                    field.setAccessible(true);
                    final Object value = field.get(owner);
                    if (valueType.isInstance(value)) return valueType.cast(value);
                    if (value != null && shouldRecurse(value.getClass())) {
                        final T found = findFirstValueDeep(value, valueType, depth - 1, visited);
                        if (found != null) return found;
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean shouldRecurse(Class<?> type) {
        final String name = type.getName();
        return name.startsWith("org.mtr.") || name.startsWith("java.util.") || type.isArray();
    }

    public static String describeFields(Object owner) {
        if (owner == null) return "<null>";
        final StringBuilder builder = new StringBuilder();
        Class<?> type = owner.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (builder.length() > 0) builder.append(", ");
                builder.append(type.getSimpleName()).append('.').append(field.getName())
                        .append(':').append(field.getType().getName());
            }
            type = type.getSuperclass();
        }
        return builder.toString();
    }
}
