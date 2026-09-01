package io.github.rrobetti.xafault;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A one-shot coordination barrier used from a scenario rule (typically via
 * {@link XaAction#gate(XaGate, Duration)}) to pause an XA call until a test
 * thread explicitly releases it.
 *
 * <p>This lets a test line up an external fault (for example disabling a
 * Toxiproxy proxy) with a precise point in the XA call sequence: the
 * resource-manager thread blocks in {@link #arriveAndAwait}, the test thread
 * observes the arrival with {@link #awaitArrival}, performs the external
 * action, and then calls {@link #release()} to let the original call
 * continue (and typically fail against the now-broken resource).
 *
 * <p>A gate is single-use: the first arrival is the only one recorded, and
 * {@link #release()} only ever needs to be called once.
 */
public final class XaGate {
    private final CountDownLatch arrived = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private final AtomicReference<XaEvent> arrivalEvent = new AtomicReference<>();

    /**
     * Called from the intercepted XA call. Records {@code event} as the
     * arrival (if none was recorded yet) and blocks the calling thread until
     * {@link #release()} is invoked or {@code timeout} elapses.
     *
     * @throws XaGateTimeoutException if the gate is not released within
     *         {@code timeout}
     */
    public void arriveAndAwait(XaEvent event, Duration timeout) {
        arrivalEvent.compareAndSet(null, event);
        arrived.countDown();
        await(released, timeout, "release");
    }

    /**
     * Blocks the calling (typically test) thread until some XA call has
     * reached the gate, then returns the event that triggered the arrival.
     *
     * @throws XaGateTimeoutException if nothing arrives within {@code timeout}
     */
    public XaEvent awaitArrival(Duration timeout) {
        await(arrived, timeout, "arrival");
        return arrivalEvent.get();
    }

    /** Frees any thread currently blocked in {@link #arriveAndAwait}. */
    public void release() {
        released.countDown();
    }

    public boolean hasArrived() {
        return arrived.getCount() == 0;
    }

    public boolean isReleased() {
        return released.getCount() == 0;
    }

    private static void await(CountDownLatch latch, Duration timeout, String what) {
        boolean completed;
        try {
            completed = latch.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new XaGateTimeoutException("Interrupted waiting for gate " + what, e);
        }
        if (!completed) {
            throw new XaGateTimeoutException("Timed out after " + timeout + " waiting for gate " + what, null);
        }
    }
}
