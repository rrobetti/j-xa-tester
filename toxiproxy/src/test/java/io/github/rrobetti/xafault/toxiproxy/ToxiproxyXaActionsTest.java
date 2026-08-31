package io.github.rrobetti.xafault.toxiproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaAction;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaRule;
import io.github.rrobetti.xafault.XaRules;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.io.IOException;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test proving the wiring from a fired {@link XaRule} through
 * {@link ToxiproxyXaActions} to a real (fake-server-backed) {@link ToxiproxyProxy}
 * call, combined with a {@link FaultInjectingXAResource} guarding a plain
 * {@link XAResource} delegate.
 */
class ToxiproxyXaActionsTest {

    private FakeToxiproxyServer server;
    private ToxiproxyClient client;

    @BeforeEach
    void start() throws IOException {
        server = new FakeToxiproxyServer();
        client = new ToxiproxyClient(server.baseUrl());
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void ruleDisablesRealProxyBeforeCommitAndThenThrowsToSimulateTheBrokenLink() throws Exception {
        ToxiproxyProxy proxy = client.createProxy("orders-db", "localhost:23306", "localhost:3306");
        assertTrue(proxy.enabled());

        FakeXAResource delegate = new FakeXAResource();
        XaScenarioEngine engine = new XaScenarioEngine();
        engine.addRule(new XaRule(XaRules.before("orders-db", XaOperation.COMMIT),
                XaAction.compose(ToxiproxyXaActions.disable(proxy), XaAction.throwException(XAException.XAER_RMFAIL))));

        XAResource resource = new FaultInjectingXAResource(delegate, engine, "orders-db", ResourceKind.JDBC);
        Xid xid = new SimpleXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.end(xid, XAResource.TMSUCCESS);

        XAException exception = assertThrows(XAException.class, () -> resource.commit(xid, true));

        assertEquals(XAException.XAER_RMFAIL, exception.errorCode);
        assertFalse(delegate.committed, "the delegate must not be called once the rule intervenes");
        assertFalse(client.getProxy("orders-db").enabled(), "the real proxy must have been disabled by the callback");
    }

    @Test
    void enableActionReEnablesAPreviouslyDisabledProxy() throws Exception {
        ToxiproxyProxy proxy = client.createProxy("orders-db", "localhost:23306", "localhost:3306");
        proxy.disable();
        assertFalse(client.getProxy("orders-db").enabled());

        FakeXAResource delegate = new FakeXAResource();
        XaScenarioEngine engine = new XaScenarioEngine();
        engine.addRule(new XaRule(XaRules.before("orders-db", XaOperation.START), ToxiproxyXaActions.enable(proxy)));

        XAResource resource = new FaultInjectingXAResource(delegate, engine, "orders-db", ResourceKind.JDBC);
        resource.start(new SimpleXid(), XAResource.TMNOFLAGS);

        assertTrue(client.getProxy("orders-db").enabled());
    }

    private static final class SimpleXid implements Xid {
        @Override
        public int getFormatId() {
            return 9;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return new byte[] {9};
        }

        @Override
        public byte[] getBranchQualifier() {
            return new byte[] {1};
        }
    }

    private static final class FakeXAResource implements XAResource {
        boolean committed;

        @Override
        public void start(Xid xid, int flags) {}

        @Override
        public void end(Xid xid, int flags) {}

        @Override
        public int prepare(Xid xid) {
            return XA_OK;
        }

        @Override
        public void commit(Xid xid, boolean onePhase) {
            committed = true;
        }

        @Override
        public void rollback(Xid xid) {}

        @Override
        public Xid[] recover(int flag) {
            return new Xid[0];
        }

        @Override
        public void forget(Xid xid) {}

        @Override
        public boolean isSameRM(XAResource xaResource) {
            return xaResource == this;
        }

        @Override
        public int getTransactionTimeout() {
            return 0;
        }

        @Override
        public boolean setTransactionTimeout(int seconds) {
            return true;
        }
    }
}
