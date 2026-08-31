package io.github.rrobetti.xafault;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.junit.jupiter.api.Test;

class FaultInjectingXAResourceTest {
    @Test
    void recordsOrderedPrepareAndCommitEvents() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        RecordingResource delegate = new RecordingResource();
        FaultInjectingXAResource resource = new FaultInjectingXAResource(delegate, engine, "orders-db", ResourceKind.JDBC);
        Xid xid = new TestXid();

        assertEquals(XAResource.XA_OK, resource.prepare(xid));
        resource.commit(xid, false);

        List<XaEvent> events = engine.journal().events();
        assertEquals(4, events.size());
        assertEquals(List.of(EventPosition.BEFORE, EventPosition.AFTER_SUCCESS, EventPosition.BEFORE, EventPosition.AFTER_SUCCESS),
                events.stream().map(XaEvent::position).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L), events.stream().map(XaEvent::sequence).toList());
        assertEquals(XAResource.XA_OK, events.get(1).returnCode());
        assertEquals(2, delegate.calls);
    }

    @Test
    void syntheticBeforeFaultSkipsDelegateAndRetainsCode() {
        XaScenarioEngine engine = new XaScenarioEngine();
        engine.addRule(new XaRule(event -> event.operation() == XaOperation.COMMIT && event.position() == EventPosition.BEFORE,
                XaAction.throwException(XAException.XAER_RMFAIL)));
        RecordingResource delegate = new RecordingResource();
        FaultInjectingXAResource resource = new FaultInjectingXAResource(delegate, engine, "payments-mq", ResourceKind.JMS);

        XAException exception = assertThrows(XAException.class, () -> resource.commit(new TestXid(), false));

        assertEquals(XAException.XAER_RMFAIL, exception.errorCode);
        assertEquals(0, delegate.calls);
        assertEquals(EventPosition.BEFORE, engine.journal().events().get(0).position());
        assertEquals(EventPosition.AFTER_FAILURE, engine.journal().events().get(1).position());
    }

    @Test
    void unwrappingPeerPreservesIsSameRmAndSnapshotsXid() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        RecordingResource first = new RecordingResource();
        RecordingResource second = new RecordingResource();
        first.sameRm = second;
        FaultInjectingXAResource firstWrapper = new FaultInjectingXAResource(first, engine, "one", ResourceKind.OTHER);
        FaultInjectingXAResource secondWrapper = new FaultInjectingXAResource(second, engine, "two", ResourceKind.OTHER);

        assertTrue(firstWrapper.isSameRM(secondWrapper));
        assertFalse(first.lastSameRm instanceof FaultInjectingXAResource);
        byte[] source = {1};
        Xid mutableXid = new TestXid(source);
        firstWrapper.start(mutableXid, XAResource.TMNOFLAGS);
        source[0] = 99;
        assertArrayEquals(new byte[] {1}, engine.journal().events().get(2).xid().globalTransactionId());
    }

    private static final class RecordingResource implements XAResource {
        int calls;
        XAResource sameRm;
        XAResource lastSameRm;

        @Override public void start(Xid xid, int flags) { calls++; }
        @Override public void end(Xid xid, int flags) { calls++; }
        @Override public int prepare(Xid xid) { calls++; return XA_OK; }
        @Override public void commit(Xid xid, boolean onePhase) { calls++; }
        @Override public void rollback(Xid xid) { calls++; }
        @Override public Xid[] recover(int flag) { calls++; return new Xid[0]; }
        @Override public void forget(Xid xid) { calls++; }
        @Override public boolean isSameRM(XAResource xaResource) { lastSameRm = xaResource; return xaResource == sameRm; }
        @Override public int getTransactionTimeout() { calls++; return 0; }
        @Override public boolean setTransactionTimeout(int seconds) { calls++; return true; }
    }

    private static final class TestXid implements Xid {
        private final byte[] global;

        TestXid() {
            this(new byte[] {1, 2});
        }

        TestXid(byte[] global) {
            this.global = global;
        }

        @Override public int getFormatId() { return 42; }
        @Override public byte[] getGlobalTransactionId() { return global; }
        @Override public byte[] getBranchQualifier() { return new byte[] {3}; }
    }
}
