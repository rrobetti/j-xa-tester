package io.github.rrobetti.xafault;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/** Small XAResource/Xid fakes shared by the core module's own tests. */
final class TestSupport {
    private TestSupport() {}

    static final class RecordingResource implements XAResource {
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

    static final class TestXid implements Xid {
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
