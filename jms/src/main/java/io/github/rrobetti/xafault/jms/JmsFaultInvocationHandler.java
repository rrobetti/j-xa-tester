package io.github.rrobetti.xafault.jms;

import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.transaction.xa.XAResource;

/**
 * Reflection-based {@link InvocationHandler} that recognizes the Jakarta
 * Messaging (or legacy {@code javax.jms}) XA object graph purely by method
 * return type name, without compiling against the API:
 *
 * <ul>
 *   <li>a method returning exactly {@link XAResource} (a JDK type) is
 *       intercepted and its result wrapped in a {@link FaultInjectingXAResource};</li>
 *   <li>a method returning an interface in package {@code jakarta.jms} or
 *       {@code javax.jms} whose simple name contains {@code "XA"} (for
 *       example {@code XAConnection}, {@code XASession}, {@code XAJMSContext})
 *       is wrapped in a further proxy using this same handler, so the chain
 *       from factory to connection to session is followed automatically;</li>
 *   <li>every other method call, including ordinary (non-XA) JMS traffic,
 *       passes straight through to the delegate.</li>
 * </ul>
 */
final class JmsFaultInvocationHandler implements InvocationHandler {
    private final Object target;
    private final XaScenarioEngine engine;
    private final String resourceId;

    JmsFaultInvocationHandler(Object target, XaScenarioEngine engine, String resourceId) {
        this.target = target;
        this.engine = engine;
        this.resourceId = resourceId;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }
        Object result;
        try {
            result = method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw cause != null ? cause : e;
        }
        if (result == null) {
            return null;
        }
        Class<?> returnType = method.getReturnType();
        if (returnType == XAResource.class) {
            return new FaultInjectingXAResource((XAResource) result, engine, resourceId, ResourceKind.JMS);
        }
        if (isXaJmsType(returnType)) {
            return JmsProxies.wrap(result, new JmsFaultInvocationHandler(result, engine, resourceId));
        }
        return result;
    }

    private Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "FaultInjectingJmsProxy(" + target + ")";
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }

    private static boolean isXaJmsType(Class<?> type) {
        if (!type.isInterface()) {
            return false;
        }
        String name = type.getName();
        boolean jmsPackage = name.startsWith("jakarta.jms.") || name.startsWith("javax.jms.");
        return jmsPackage && type.getSimpleName().contains("XA");
    }
}
