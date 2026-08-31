package io.github.rrobetti.xafault.jms;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builds {@link Proxy} instances that implement every interface a delegate
 * object implements (walking superclasses and super-interfaces), so a
 * wrapped Jakarta Messaging object remains usable through whatever mix of
 * spec and provider interfaces the original exposed.
 */
final class JmsProxies {
    private JmsProxies() {}

    static Object wrap(Object target, InvocationHandler handler) {
        Class<?> targetClass = target.getClass();
        Set<Class<?>> interfaces = collectInterfaces(targetClass);
        if (interfaces.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot create a fault-injecting proxy for " + targetClass.getName()
                            + " because it implements no interfaces");
        }
        ClassLoader loader = targetClass.getClassLoader();
        if (loader == null) {
            loader = ClassLoader.getSystemClassLoader();
        }
        return Proxy.newProxyInstance(loader, interfaces.toArray(new Class<?>[0]), handler);
    }

    private static Set<Class<?>> collectInterfaces(Class<?> type) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
                addInterface(result, iface);
            }
        }
        return result;
    }

    private static void addInterface(Set<Class<?>> result, Class<?> iface) {
        if (result.add(iface)) {
            for (Class<?> superInterface : iface.getInterfaces()) {
                addInterface(result, superInterface);
            }
        }
    }
}
