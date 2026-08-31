package io.github.rrobetti.xafault.jms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rrobetti.xafault.EventPosition;
import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaAction;
import io.github.rrobetti.xafault.XaEvent;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaRule;
import io.github.rrobetti.xafault.XaRules;
import io.github.rrobetti.xafault.XaScenarioEngine;
import jakarta.jms.XAConnection;
import jakarta.jms.XAConnectionFactory;
import jakarta.jms.XASession;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.junit.jupiter.api.Test;

class FaultInjectingJmsTest {

    @Test
    void wrapsFactoryConnectionSessionChainAndInterceptsXaResource() throws Exception {
        List<String> calls = new ArrayList<>();
        FakeXAResource fakeResource = new FakeXAResource();
        XAConnectionFactory factory = FakeJmsProvider.connectionFactory(() -> fakeResource, calls);
        XaScenarioEngine engine = new XaScenarioEngine();

        XAConnectionFactory wrapped = FaultInjectingJms.wrap(factory, engine, "orders-mq");
        XAConnection connection = wrapped.createXAConnection();
        XASession session = connection.createXASession();
        XAResource resource = session.getXAResource();

        assertInstanceOf(FaultInjectingXAResource.class, resource);
        assertNotSame(fakeResource, resource);

        Xid xid = new SimpleXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.end(xid, XAResource.TMSUCCESS);
        assertEquals(XAResource.XA_OK, resource.prepare(xid));
        resource.commit(xid, false);

        assertEquals(1, fakeResource.commitCalls, "the real provider resource must still receive the calls");
        assertEquals(List.of("XAConnectionFactory#createXAConnection", "XAConnection#createXASession",
                "XASession#getXAResource"), calls);

        List<XaEvent> events = engine.journal().events();
        assertTrue(events.stream().allMatch(e -> e.resourceId().equals("orders-mq") && e.resourceKind() == ResourceKind.JMS));
        assertTrue(events.stream().noneMatch(e -> e.position() == EventPosition.AFTER_FAILURE));
    }

    @Test
    void nonXaMethodsPassThroughUntouched() throws Exception {
        List<String> calls = new ArrayList<>();
        XAConnectionFactory factory = FakeJmsProvider.connectionFactory(FakeXAResource::new, calls);
        XaScenarioEngine engine = new XaScenarioEngine();
        XAConnectionFactory wrapped = FaultInjectingJms.wrap(factory, engine, "orders-mq");

        XAConnection connection = wrapped.createXAConnection();
        connection.setClientID("client-1");
        connection.start();
        connection.stop();

        assertTrue(calls.contains("XAConnection#setClientID"));
        assertTrue(calls.contains("XAConnection#start"));
        assertTrue(calls.contains("XAConnection#stop"));
        assertEquals(0, engine.journal().events().size(), "non-XA calls must not be recorded by the engine");
    }

    @Test
    void proxyImplementsAllDelegateInterfacesIncludingNonJmsOnes() {
        List<String> calls = new ArrayList<>();
        XAConnectionFactory delegate = new SerializableFakeFactory(calls);
        XaScenarioEngine engine = new XaScenarioEngine();

        XAConnectionFactory wrapped = FaultInjectingJms.wrap(delegate, engine, "orders-mq");

        assertInstanceOf(XAConnectionFactory.class, wrapped);
        assertInstanceOf(Serializable.class, wrapped);
    }

    @Test
    void ruleInterceptsCommitThroughTheFullProxyChainWithoutCallingTheRealResource() throws Exception {
        List<String> calls = new ArrayList<>();
        FakeXAResource fakeResource = new FakeXAResource();
        XAConnectionFactory factory = FakeJmsProvider.connectionFactory(() -> fakeResource, calls);
        XaScenarioEngine engine = new XaScenarioEngine();
        engine.addRule(new XaRule(XaRules.before("orders-mq", XaOperation.COMMIT),
                XaAction.throwException(XAException.XAER_RMFAIL)));

        XAConnectionFactory wrapped = FaultInjectingJms.wrap(factory, engine, "orders-mq");
        XAResource resource = wrapped.createXAConnection().createXASession().getXAResource();

        XAException exception = assertThrows(XAException.class, () -> resource.commit(new SimpleXid(), false));

        assertEquals(XAException.XAER_RMFAIL, exception.errorCode);
        assertEquals(0, fakeResource.commitCalls, "the synthetic BEFORE fault must prevent the delegate call");
    }

    private static final class SimpleXid implements Xid {
        @Override public int getFormatId() { return 7; }
        @Override public byte[] getGlobalTransactionId() { return new byte[] {1, 2, 3}; }
        @Override public byte[] getBranchQualifier() { return new byte[] {4}; }
    }

    /** A delegate implementing both an XA-JMS interface and an unrelated marker interface. */
    private static final class SerializableFakeFactory implements XAConnectionFactory, Serializable {
        private final transient List<String> calls;

        SerializableFakeFactory(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public jakarta.jms.XAConnection createXAConnection() {
            calls.add("createXAConnection");
            return null;
        }

        @Override
        public jakarta.jms.XAConnection createXAConnection(String userName, String password) {
            return createXAConnection();
        }

        @Override
        public jakarta.jms.XAJMSContext createXAContext() {
            return null;
        }

        @Override
        public jakarta.jms.XAJMSContext createXAContext(String userName, String password) {
            return null;
        }
    }
}
