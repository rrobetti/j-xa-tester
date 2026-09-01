package io.github.rrobetti.xafault;

import java.util.Arrays;
import javax.transaction.xa.Xid;

public record XidSnapshot(int formatId, byte[] globalTransactionId, byte[] branchQualifier) {
    public XidSnapshot {
        globalTransactionId = globalTransactionId == null ? null : globalTransactionId.clone();
        branchQualifier = branchQualifier == null ? null : branchQualifier.clone();
    }

    public static XidSnapshot from(Xid xid) {
        return xid == null ? null : new XidSnapshot(xid.getFormatId(), xid.getGlobalTransactionId(), xid.getBranchQualifier());
    }

    @Override
    public byte[] globalTransactionId() {
        return globalTransactionId == null ? null : globalTransactionId.clone();
    }

    @Override
    public byte[] branchQualifier() {
        return branchQualifier == null ? null : branchQualifier.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof XidSnapshot that
                && formatId == that.formatId
                && Arrays.equals(globalTransactionId, that.globalTransactionId)
                && Arrays.equals(branchQualifier, that.branchQualifier);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Integer.hashCode(formatId) + Arrays.hashCode(globalTransactionId))
                + Arrays.hashCode(branchQualifier);
    }
}
