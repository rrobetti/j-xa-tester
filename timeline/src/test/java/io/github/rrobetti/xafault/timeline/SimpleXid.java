package io.github.rrobetti.xafault.timeline;

import javax.transaction.xa.Xid;

/** Minimal fixed {@link Xid} fake used by the timeline module's tests. */
final class SimpleXid implements Xid {
    private final int formatId;

    SimpleXid(int formatId) {
        this.formatId = formatId;
    }

    @Override
    public int getFormatId() {
        return formatId;
    }

    @Override
    public byte[] getGlobalTransactionId() {
        return new byte[] {(byte) formatId};
    }

    @Override
    public byte[] getBranchQualifier() {
        return new byte[] {0};
    }
}
