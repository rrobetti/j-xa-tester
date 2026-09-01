package io.github.rrobetti.xafault.jdbc;

import io.github.rrobetti.japiproxy.jdbc.JdbcProxy;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaFaultInvocationFilter;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.util.Objects;
import javax.sql.XADataSource;

/** Creates XA data sources that inject faults through J API Proxy. */
public final class FaultInjectingJdbc {
    private FaultInjectingJdbc() {}

    public static XADataSource wrap(XADataSource delegate, XaScenarioEngine engine, String resourceId) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(resourceId, "resourceId");
        return JdbcProxy.wrapXa(delegate, resourceId,
                new XaFaultInvocationFilter(engine, resourceId, ResourceKind.JDBC));
    }
}
