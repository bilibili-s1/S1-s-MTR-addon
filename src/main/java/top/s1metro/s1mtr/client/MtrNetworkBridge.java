package top.s1metro.s1mtr.client;

import org.mtr.core.data.Rail;
import top.s1metro.s1mtr.S1MtrAddon;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Reflection bridge to MTR's client networking API. MTR 4.1 beta moved these
 * classes out of the old mapping packages, so this code deliberately accepts
 * both layouts and logs every selected member when saving.
 */
public final class MtrNetworkBridge {
    private MtrNetworkBridge() {
    }

    public static boolean sendRailUpdate(Rail rail) {
        try {
            final ClassLoader loader = MtrNetworkBridge.class.getClassLoader();
            final Class<?> clientDataClass = findClass(loader,
                    "org.mtr.client.MinecraftClientData",
                    "org.mtr.mod.client.MinecraftClientData");
            final Method getInstance = findZeroArgumentStaticMethod(clientDataClass, "getInstance");
            final Object clientData = invoke(getInstance, null);
            if (clientData == null) throw new IllegalStateException("MinecraftClientData.getInstance() returned null");

            final Class<?> requestClass = findClass(loader, "org.mtr.core.operation.UpdateDataRequest");
            Object request = constructWithLeadingArgument(requestClass, clientData, "UpdateDataRequest");

            final Method addRail = findCompatibleSingleArgumentMethod(requestClass, "addRail", rail);
            final Object addRailResult = invoke(addRail, request, rail);
            // Some MTR core revisions use a fluent/immutable request builder. Keep
            // the returned request when it is compatible instead of assuming the
            // original instance was mutated.
            if (addRailResult != null && requestClass.isInstance(addRailResult)) {
                request = addRailResult;
            }
            S1MtrAddon.LOGGER.info("Prepared MTR update request using {} and {} (returned={})",
                    describeMember(request.getClass()), describe(addRail),
                    addRailResult == null ? "null" : addRailResult.getClass().getName());

            final Class<?> packetClass = findClass(loader,
                    "org.mtr.packet.PacketUpdateData",
                    "org.mtr.mod.packet.PacketUpdateData");
            final Object packet = constructWithLeadingArgument(packetClass, request, "PacketUpdateData");

            final Class<?> registryClass = findClass(loader,
                    "org.mtr.registry.RegistryClient",
                    "org.mtr.mapping.registry.RegistryClient");
            final Method send = findCompatibleStaticMethod(registryClass, "sendPacketToServer", packet);
            invoke(send, null, packet);
            S1MtrAddon.LOGGER.info("Submitted MTR rail update using {}", describe(send));
            return true;
        } catch (Throwable throwable) {
            final Throwable root = unwrap(throwable);
            S1MtrAddon.LOGGER.error("Failed to send MTR rail update: " + root, root);
            logApiShape();
            return false;
        }
    }

    private static Object constructWithLeadingArgument(Class<?> owner, Object argument, String label)
            throws ReflectiveOperationException {
        final List<Constructor<?>> candidates = new ArrayList<>();
        for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
            final Class<?>[] types = constructor.getParameterTypes();
            if (types.length > 0 && isCompatible(types[0], argument)) candidates.add(constructor);
        }
        // Prefer the exact one-argument constructor. This avoids accidentally
        // selecting a larger internal constructor and filling required reference
        // parameters with null merely because reflection returned it first.
        candidates.sort(Comparator
                .comparingInt((Constructor<?> constructor) -> constructor.getParameterCount() == 1 ? 0 : 1)
                .thenComparingInt(Constructor::getParameterCount));

        Throwable last = null;
        for (Constructor<?> constructor : candidates) {
            try {
                final Class<?>[] types = constructor.getParameterTypes();
                final Object[] args = new Object[types.length];
                args[0] = argument;
                for (int i = 1; i < types.length; i++) args[i] = defaultValue(types[i]);
                constructor.setAccessible(true);
                final Object value = constructor.newInstance(args);
                S1MtrAddon.LOGGER.info("Constructed {} using {}", label, describe(constructor));
                return value;
            } catch (Throwable throwable) {
                last = unwrap(throwable);
                S1MtrAddon.LOGGER.warn("Rejected {} constructor {}: {}", label, describe(constructor), last.toString());
            }
        }

        for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                constructor.setAccessible(true);
                final Object value = constructor.newInstance();
                S1MtrAddon.LOGGER.info("Constructed {} using no-argument {}", label, describe(constructor));
                return value;
            }
        }
        final NoSuchMethodException exception = new NoSuchMethodException(
                "No compatible " + label + " constructor on " + owner.getName() + "; available="
                        + Arrays.toString(owner.getDeclaredConstructors()));
        if (last != null) exception.initCause(last);
        throw exception;
    }

    private static Method findZeroArgumentStaticMethod(Class<?> owner, String name) throws NoSuchMethodException {
        for (Method method : allMethods(owner)) {
            if (method.getName().equals(name) && method.getParameterCount() == 0
                    && Modifier.isStatic(method.getModifiers())) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + '.' + name + "()");
    }

    private static Method findCompatibleSingleArgumentMethod(Class<?> owner, String name, Object argument)
            throws NoSuchMethodException {
        final List<Method> matches = new ArrayList<>();
        for (Method method : allMethods(owner)) {
            final Class<?>[] types = method.getParameterTypes();
            if (method.getName().equals(name) && types.length == 1 && isCompatible(types[0], argument)) {
                matches.add(method);
            }
        }
        matches.sort(Comparator.comparingInt(method -> method.getParameterTypes()[0] == argument.getClass() ? 0 : 1));
        if (!matches.isEmpty()) {
            final Method method = matches.get(0);
            method.setAccessible(true);
            return method;
        }
        throw new NoSuchMethodException(owner.getName() + '.' + name + '(' + argument.getClass().getName() + ')');
    }

    private static Method findCompatibleStaticMethod(Class<?> owner, String name, Object argument)
            throws NoSuchMethodException {
        for (Method method : allMethods(owner)) {
            final Class<?>[] types = method.getParameterTypes();
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)
                    && types.length == 1 && isCompatible(types[0], argument)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + '.' + name + '(' + argument.getClass().getName() + ')');
    }

    private static List<Method> allMethods(Class<?> owner) {
        final List<Method> methods = new ArrayList<>();
        Class<?> current = owner;
        while (current != null) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        for (Method method : owner.getMethods()) if (!methods.contains(method)) methods.add(method);
        return methods;
    }

    private static boolean isCompatible(Class<?> parameterType, Object argument) {
        if (argument == null) return !parameterType.isPrimitive();
        return parameterType.isInstance(argument) || parameterType.isAssignableFrom(argument.getClass());
    }

    private static Object invoke(Method method, Object owner, Object... args) throws ReflectiveOperationException {
        try {
            return method.invoke(owner, args);
        } catch (InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw exception;
        }
    }

    private static Class<?> findClass(ClassLoader loader, String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return Class.forName(name, true, loader);
            } catch (ClassNotFoundException exception) {
                last = exception;
            }
        }
        throw last == null ? new ClassNotFoundException() : last;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            current = invocation.getCause();
        }
        return current;
    }

    private static String describe(Constructor<?> constructor) {
        return constructor.getDeclaringClass().getName() + Arrays.toString(constructor.getParameterTypes());
    }

    private static String describe(Method method) {
        return method.getDeclaringClass().getName() + '.' + method.getName()
                + Arrays.toString(method.getParameterTypes()) + " -> " + method.getReturnType().getName();
    }

    private static String describeMember(Class<?> type) {
        return type == null ? "null" : type.getName();
    }

    private static void logApiShape() {
        try {
            final ClassLoader loader = MtrNetworkBridge.class.getClassLoader();
            for (String name : new String[]{
                    "org.mtr.core.operation.UpdateDataRequest",
                    "org.mtr.packet.PacketUpdateData",
                    "org.mtr.registry.RegistryClient"
            }) {
                try {
                    final Class<?> type = Class.forName(name, false, loader);
                    S1MtrAddon.LOGGER.error("MTR API diagnostic {} constructors={}", name,
                            Arrays.toString(type.getDeclaredConstructors()));
                    for (Method method : type.getDeclaredMethods()) {
                        if (method.getName().equals("addRail") || method.getName().equals("sendPacketToServer")) {
                            S1MtrAddon.LOGGER.error("MTR API diagnostic method {}", describe(method));
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
