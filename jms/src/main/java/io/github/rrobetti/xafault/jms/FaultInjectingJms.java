package io.github.rrobetti.xafault.jms;

import io.github.rrobetti.japiproxy.jms.JmsProxy;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaFaultInvocationFilter;
import io.github.rrobetti.xafault.XaScenarioEngine;
import jakarta.jms.XAConnectionFactory;
import java.util.Objects;

/**
 * Wraps a Jakarta Messaging {@code XAConnectionFactory}, {@code XAConnection},
 * or {@code XASession} so that the {@code javax.transaction.xa.XAResource} it
 * eventually hands out is a {@link FaultInjectingXAResource}.
 *
 * <p>This adapter uses J API Proxy's recursive Jakarta Messaging adapter, so
 * the XA factory, connection, session, and resource are wrapped without
 * manually creating dynamic proxies. Non-XA methods, including ordinary
 * message production/consumption, always pass straight through to the
 * delegate.
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

    public static XAConnectionFactory wrap(XAConnectionFactory delegate, XaScenarioEngine engine, String resourceId) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(resourceId, "resourceId");
        return JmsProxy.wrapXa(delegate, resourceId,
                new XaFaultInvocationFilter(engine, resourceId, ResourceKind.JMS));
    }
}
