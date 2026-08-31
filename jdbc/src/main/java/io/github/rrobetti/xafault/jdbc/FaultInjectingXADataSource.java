package io.github.rrobetti.xafault.jdbc;

import io.github.rrobetti.xafault.XaScenarioEngine;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.XAConnection;
import javax.sql.XADataSource;

/**
 * Wraps a provider's {@link XADataSource} so every {@link XAConnection} it
 * hands out (normal or recovery) is a {@link FaultInjectingXAConnection}
 * sharing one {@link XaScenarioEngine} and {@code resourceId}. This is the
 * usual entry point for the JDBC adapter: construct one instance per logical
 * database and hand it to whatever creates connections for the transaction
 * manager.
 *
 * <p>{@code resourceId} is an application-chosen safe alias used in
 * recorded events; do not put a connection URL, username, or other secret
 * in it.
 */
public final class FaultInjectingXADataSource implements XADataSource {
    private final XADataSource delegate;
    private final XaScenarioEngine engine;
    private final String resourceId;

    public FaultInjectingXADataSource(XADataSource delegate, XaScenarioEngine engine, String resourceId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
    }

    public static FaultInjectingXADataSource wrap(XADataSource delegate, XaScenarioEngine engine, String resourceId) {
        return new FaultInjectingXADataSource(delegate, engine, resourceId);
    }

    @Override
    public XAConnection getXAConnection() throws SQLException {
        return new FaultInjectingXAConnection(delegate.getXAConnection(), engine, resourceId);
    }

    @Override
    public XAConnection getXAConnection(String user, String password) throws SQLException {
        return new FaultInjectingXAConnection(delegate.getXAConnection(user, password), engine, resourceId);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
