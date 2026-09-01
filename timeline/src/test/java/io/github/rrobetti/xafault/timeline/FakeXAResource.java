package io.github.rrobetti.xafault.timeline;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/** Minimal in-memory {@link XAResource} fake used by the timeline module's tests. */
final class FakeXAResource implements XAResource {
    @Override
    public void start(Xid xid, int flags) {}

    @Override
    public void end(Xid xid, int flags) {}

    @Override
    public int prepare(Xid xid) {
        return XA_OK;
    }

    @Override
    public void commit(Xid xid, boolean onePhase) {}

    @Override
    public void rollback(Xid xid) {}

    @Override
    public Xid[] recover(int flag) {
        return new Xid[0];
    }

    @Override
    public void forget(Xid xid) {}

    @Override
    public boolean isSameRM(XAResource xaResource) throws XAException {
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
