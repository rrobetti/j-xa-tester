package io.github.rrobetti.xafault.jms;

import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.util.Objects;

/**
 * Wraps a Jakarta Messaging {@code XAConnectionFactory}, {@code XAConnection},
 * or {@code XASession} so that the {@code javax.transaction.xa.XAResource} it
 * eventually hands out is a {@link FaultInjectingXAResource}.
 *
 * <p>This adapter never compiles against the {@code jakarta.jms} API (or any
 * provider). It builds a {@link java.lang.reflect.Proxy} that implements
 * whatever interfaces the delegate implements and recognizes the handful of
 * XA-specific return types by name, so any provider conforming to the
 * Jakarta Messaging (or legacy {@code javax.jms}) contract works without a
 * compile-time dependency. Non-XA methods, including ordinary message
 * production/consumption, always pass straight through to the delegate.
 *
 * <pre>{@code
 * XAConnectionFactory raw = lookupProviderFactory();
 * XaScenarioEngine engine = new XaScenarioEngine();
 * XAConnectionFactory wrapped = FaultInjectingJms.wrap(raw, engine, "orders-mq");
 *
 * XAConnection connection = wrapped.createXAConnection();
 * XASession session = connection.createXASession();
 * XAResource resource = session.getXAResource(); // a FaultInjectingXAResource
 * }</pre>
 *
 * <p>{@code resourceId} is an application-chosen safe alias used in recorded
 * events; do not put a broker URL, username, or other secret in it.
 */
public final class FaultInjectingJms {
    private FaultInjectingJms() {}

    @SuppressWarnings("unchecked")
    public static <T> T wrap(T delegate, XaScenarioEngine engine, String resourceId) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(resourceId, "resourceId");
        return (T) JmsProxies.wrap(delegate, new JmsFaultInvocationHandler(delegate, engine, resourceId));
    }
}
