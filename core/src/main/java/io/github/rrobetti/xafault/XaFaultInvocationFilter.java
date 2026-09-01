package io.github.rrobetti.xafault;

import io.github.rrobetti.japiproxy.core.InterfaceProxy;
import io.github.rrobetti.japiproxy.core.InvocationChain;
import io.github.rrobetti.japiproxy.core.InvocationContext;
import io.github.rrobetti.japiproxy.core.InvocationFilter;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import javax.transaction.xa.XAResource;

/** Applies XA fault injection to XA resources encountered by a J API Proxy adapter. */
public final class XaFaultInvocationFilter implements InvocationFilter {
    private final XaScenarioEngine engine;
    private final String resourceId;
    private final ResourceKind resourceKind;
    private final Map<XAResource, FaultInjectingXAResource> resources =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public XaFaultInvocationFilter(XaScenarioEngine engine, String resourceId, ResourceKind resourceKind) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.resourceKind = Objects.requireNonNull(resourceKind, "resourceKind");
    }

    @Override
    public Object intercept(InvocationContext invocation, InvocationChain chain) throws Throwable {
        if (invocation.interfaceType() != XAResource.class) {
            return chain.proceed(invocation);
        }
        XAResource delegate = (XAResource) invocation.delegate();
        FaultInjectingXAResource resource = resources.computeIfAbsent(delegate,
                key -> new FaultInjectingXAResource(key, engine, resourceId, resourceKind));
        try {
            return invocation.method().invoke(resource, adaptArguments(invocation));
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static Object[] adaptArguments(InvocationContext invocation) {
        Object[] arguments = invocation.arguments();
        if (!"isSameRM".equals(invocation.method().getName()) || arguments.length == 0) {
            return arguments;
        }
        Object[] adapted = arguments.clone();
        adapted[0] = InterfaceProxy.unwrap(arguments[0]);
        return adapted;
    }
}
