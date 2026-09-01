package io.github.rrobetti.xafault.jms;

import jakarta.jms.XAConnectionFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Supplier;
import javax.transaction.xa.XAResource;

/**
 * A tiny dynamic-proxy-based fake Jakarta Messaging provider. Implementing
 * {@code XAConnectionFactory}/{@code XAConnection}/{@code XASession} by hand
 * would require stubbing dozens of unrelated {@code Connection}/{@code
 * Session} methods; instead this fake answers the handful of calls the
 * adapter and its tests care about and returns harmless defaults for
 * everything else, recording every invocation for assertions.
 */
final class FakeJmsProvider {
    private FakeJmsProvider() {}

    static XAConnectionFactory connectionFactory(Supplier<XAResource> resourceSupplier, List<String> calls) {
        return (XAConnectionFactory) Proxy.newProxyInstance(
                FakeJmsProvider.class.getClassLoader(),
                new Class<?>[] {XAConnectionFactory.class},
                new FakeHandler(resourceSupplier, calls, "XAConnectionFactory"));
    }

    private static final class FakeHandler implements InvocationHandler {
        private final Supplier<XAResource> resourceSupplier;
        private final List<String> calls;
        private final String label;

        FakeHandler(Supplier<XAResource> resourceSupplier, List<String> calls, String label) {
            this.resourceSupplier = resourceSupplier;
            this.calls = calls;
            this.label = label;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            calls.add(label + "#" + name);
            switch (name) {
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "Fake" + label;
                case "getXAResource":
                    return resourceSupplier.get();
                default:
                    return defaultOrNested(method);
            }
        }

        private Object defaultOrNested(Method method) {
            Class<?> returnType = method.getReturnType();
            if (returnType.isInterface() && returnType.getName().startsWith("jakarta.jms.")) {
                String childLabel = returnType.getSimpleName();
                return Proxy.newProxyInstance(FakeJmsProvider.class.getClassLoader(), new Class<?>[] {returnType},
                        new FakeHandler(resourceSupplier, calls, childLabel));
            }
            return defaultValue(returnType);
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) {
                return null;
            }
            if (type == boolean.class) {
                return Boolean.FALSE;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == double.class) {
                return 0d;
            }
            if (type == float.class) {
                return 0f;
            }
            return 0;
        }
    }
}
