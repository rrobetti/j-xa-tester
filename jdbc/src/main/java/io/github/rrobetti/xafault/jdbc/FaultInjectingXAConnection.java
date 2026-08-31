package io.github.rrobetti.xafault.jdbc;

import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.ConnectionEventListener;
import javax.sql.StatementEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

/**
 * Wraps a provider's {@link XAConnection} so that {@link #getXAResource()}
 * returns a {@link FaultInjectingXAResource} instead of the raw delegate.
 * The wrapped resource is created lazily and cached so repeated calls (some
 * transaction managers call it more than once per connection) return the
 * same instance, matching typical pooled-connection expectations.
 *
 * <p>Every other method, including {@link #getConnection()}, delegates
 * directly: this adapter only instruments the XA control path, not SQL
 * execution.
 */
public final class FaultInjectingXAConnection implements XAConnection {
    private final XAConnection delegate;
    private final XaScenarioEngine engine;
    private final String resourceId;
    private volatile XAResource wrappedResource;

    public FaultInjectingXAConnection(XAConnection delegate, XaScenarioEngine engine, String resourceId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
    }

    @Override
    public synchronized XAResource getXAResource() throws SQLException {
        if (wrappedResource == null) {
            wrappedResource = new FaultInjectingXAResource(delegate.getXAResource(), engine, resourceId, ResourceKind.JDBC);
        }
        return wrappedResource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public void close() throws SQLException {
        delegate.close();
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener listener) {
        delegate.addConnectionEventListener(listener);
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        delegate.removeConnectionEventListener(listener);
    }

    @Override
    public void addStatementEventListener(StatementEventListener listener) {
        delegate.addStatementEventListener(listener);
    }

    @Override
    public void removeStatementEventListener(StatementEventListener listener) {
        delegate.removeStatementEventListener(listener);
    }
}
