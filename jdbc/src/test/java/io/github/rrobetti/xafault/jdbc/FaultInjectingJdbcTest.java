package io.github.rrobetti.xafault.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rrobetti.xafault.EventPosition;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaAction;
import io.github.rrobetti.xafault.XaEvent;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaRule;
import io.github.rrobetti.xafault.XaRules;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * Exercises the JDBC adapter against a real, embedded H2 {@code XADataSource}
 * (no fakes) so the wrapping is proven against an actual spec-compliant
 * driver rather than only a hand-rolled test double.
 */
class FaultInjectingJdbcTest {

    @Test
    void wrapsRealH2XaConnectionAndRecordsPrepareCommit() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        XADataSource wrapped = FaultInjectingJdbc.wrap(newH2DataSource(), engine, "orders-db");

        // javax.sql.XAConnection does not extend AutoCloseable, so it cannot be used
        // directly in a try-with-resources statement.
        XAConnection xaConnection = wrapped.getXAConnection();
        try {
            XAResource resource = xaConnection.getXAResource();
            try (Connection connection = xaConnection.getConnection()) {
                Xid xid = new SimpleXid(1);
                resource.start(xid, XAResource.TMNOFLAGS);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS demo(id INT PRIMARY KEY)");
                    statement.execute("INSERT INTO demo(id) VALUES (1)");
                }
                resource.end(xid, XAResource.TMSUCCESS);
                assertEquals(XAResource.XA_OK, resource.prepare(xid));
                resource.commit(xid, false);
            }
        } finally {
            xaConnection.close();
        }

        List<XaEvent> events = engine.journal().events();
        List<XaOperation> operations = events.stream().map(XaEvent::operation).distinct().toList();
        assertTrue(operations.containsAll(List.of(XaOperation.START, XaOperation.END, XaOperation.PREPARE, XaOperation.COMMIT)));
        assertTrue(events.stream().allMatch(e -> e.resourceId().equals("orders-db") && e.resourceKind() == ResourceKind.JDBC));
        assertTrue(events.stream().noneMatch(e -> e.position() == EventPosition.AFTER_FAILURE));
    }

    @Test
    void getXAResourceIsCachedAcrossCalls() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        XADataSource wrapped = FaultInjectingJdbc.wrap(newH2DataSource(), engine, "orders-db");

        XAConnection xaConnection = wrapped.getXAConnection();
        try {
            XAResource first = xaConnection.getXAResource();
            XAResource second = xaConnection.getXAResource();
            assertSame(first, second, "repeated getXAResource() calls must return the same wrapper");
        } finally {
            xaConnection.close();
        }
    }

    @Test
    void ruleInjectsCommitFailureWithoutTouchingRealDatabase() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        engine.addRule(new XaRule(XaRules.before("orders-db", XaOperation.COMMIT),
                XaAction.throwException(XAException.XAER_RMFAIL)));
        XADataSource wrapped = FaultInjectingJdbc.wrap(newH2DataSource(), engine, "orders-db");

        XAConnection xaConnection = wrapped.getXAConnection();
        try {
            XAResource resource = xaConnection.getXAResource();
            Xid xid = new SimpleXid(2);
            resource.start(xid, XAResource.TMNOFLAGS);
            resource.end(xid, XAResource.TMSUCCESS);
            resource.prepare(xid);

            XAException exception = assertThrows(XAException.class, () -> resource.commit(xid, false));
            assertEquals(XAException.XAER_RMFAIL, exception.errorCode);
        } finally {
            xaConnection.close();
        }
    }

    private static JdbcDataSource newH2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static final class SimpleXid implements Xid {
        private final int id;

        SimpleXid(int id) {
            this.id = id;
        }

        @Override public int getFormatId() { return 0x4a58; }
        @Override public byte[] getGlobalTransactionId() { return ("gtrid-" + id).getBytes(StandardCharsets.UTF_8); }
        @Override public byte[] getBranchQualifier() { return ("branch-" + id).getBytes(StandardCharsets.UTF_8); }
    }
}
