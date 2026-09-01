package io.github.rrobetti.xafault;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

public final class FaultInjectingXAResource implements XAResource {
    private final XAResource delegate;
    private final XaScenarioEngine engine;
    private final String resourceId;
    private final ResourceKind resourceKind;
    private final String instanceId = UUID.randomUUID().toString();
    private final AtomicLong resourceOrdinal = new AtomicLong();

    public FaultInjectingXAResource(XAResource delegate, XaScenarioEngine engine, String resourceId, ResourceKind resourceKind) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.resourceKind = Objects.requireNonNull(resourceKind, "resourceKind");
    }

    @Override public void start(Xid xid, int flags) throws XAException { invokeVoid(XaOperation.START, xid, flags, null, () -> delegate.start(xid, flags)); }
    @Override public void end(Xid xid, int flags) throws XAException { invokeVoid(XaOperation.END, xid, flags, null, () -> delegate.end(xid, flags)); }
    @Override public int prepare(Xid xid) throws XAException { return invokeInt(XaOperation.PREPARE, xid, null, null, () -> delegate.prepare(xid)); }
    @Override public void commit(Xid xid, boolean onePhase) throws XAException { invokeVoid(XaOperation.COMMIT, xid, null, onePhase, () -> delegate.commit(xid, onePhase)); }
    @Override public void rollback(Xid xid) throws XAException { invokeVoid(XaOperation.ROLLBACK, xid, null, null, () -> delegate.rollback(xid)); }
    @Override public Xid[] recover(int flags) throws XAException { return invoke(XaOperation.RECOVER, null, flags, null, () -> delegate.recover(flags)); }
    @Override public void forget(Xid xid) throws XAException { invokeVoid(XaOperation.FORGET, xid, null, null, () -> delegate.forget(xid)); }
    @Override public boolean isSameRM(XAResource other) throws XAException {
        XAResource unwrapped = other instanceof FaultInjectingXAResource wrapper ? wrapper.delegate : other;
        return invoke(XaOperation.IS_SAME_RM, null, null, null, () -> delegate.isSameRM(unwrapped));
    }
    @Override public int getTransactionTimeout() throws XAException { return invokeInt(XaOperation.GET_TIMEOUT, null, null, null, delegate::getTransactionTimeout); }
    @Override public boolean setTransactionTimeout(int seconds) throws XAException { return invoke(XaOperation.SET_TIMEOUT, null, seconds, null, () -> delegate.setTransactionTimeout(seconds)); }

    private void invokeVoid(XaOperation operation, Xid xid, Integer flags, Boolean onePhase, XaVoidCall call) throws XAException {
        invoke(operation, xid, flags, onePhase, () -> { call.run(); return null; });
    }

    private int invokeInt(XaOperation operation, Xid xid, Integer flags, Boolean onePhase, XaIntCall call) throws XAException {
        return invoke(operation, xid, flags, onePhase, call::run);
    }

    private <T> T invoke(XaOperation operation, Xid xid, Integer flags, Boolean onePhase, XaCall<T> call) throws XAException {
        long globalOrdinal = engine.nextOperationOrdinal();
        long localOrdinal = resourceOrdinal.incrementAndGet();
        try {
            event(globalOrdinal, localOrdinal, operation, EventPosition.BEFORE, xid, flags, onePhase, null, null);
            T result = call.run();
            Integer returnCode = result instanceof Integer value ? value : null;
            event(globalOrdinal, localOrdinal, operation, EventPosition.AFTER_SUCCESS, xid, flags, onePhase, returnCode, null);
            return result;
        } catch (XAException exception) {
            event(globalOrdinal, localOrdinal, operation, EventPosition.AFTER_FAILURE, xid, flags, onePhase, null, XaError.from(exception));
            throw exception;
        }
    }

    private void event(long globalOrdinal, long localOrdinal, XaOperation operation, EventPosition position, Xid xid,
                       Integer flags, Boolean onePhase, Integer returnCode, XaError error) throws XAException {
        engine.record(new XaEvent(0, Instant.now(), resourceId, resourceKind, instanceId, operation, position,
                XidSnapshot.from(xid), flags, onePhase, returnCode, error, Thread.currentThread().getName(),
                globalOrdinal, localOrdinal));
    }

    @FunctionalInterface private interface XaCall<T> { T run() throws XAException; }
    @FunctionalInterface private interface XaVoidCall { void run() throws XAException; }
    @FunctionalInterface private interface XaIntCall { int run() throws XAException; }
}
