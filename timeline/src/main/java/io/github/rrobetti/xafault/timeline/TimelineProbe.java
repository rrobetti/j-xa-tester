package io.github.rrobetti.xafault.timeline;

import io.github.rrobetti.xafault.XaEvent;
import io.github.rrobetti.xafault.XaEventListener;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A blocking wait for a specific {@link XaEvent} to be recorded by an
 * {@link XaScenarioEngine}, used to coordinate a test thread with whatever
 * thread(s) are actually driving XA calls (for example a background thread
 * running application code against a fault-injecting resource).
 */
public final class TimelineProbe {
    private static final int QUEUE_CAPACITY = 256;

    private final XaScenarioEngine engine;

    public TimelineProbe(XaScenarioEngine engine) {
        this.engine = engine;
    }

    /**
     * Returns an already-recorded event matching {@code predicate} if one
     * exists; otherwise blocks the calling thread until one is recorded, or
     * {@code timeout} elapses.
     *
     * <p>This is a check-then-wait: it first scans the entire journal as it
     * stands right now, so a matching event that already happened (for
     * example a background thread that raced ahead of the calling thread) is
     * returned immediately rather than waited for. Only if nothing matches
     * yet does it start waiting for new events.
     *
     * <p>To stay race-free even if a matching event is recorded between
     * taking that initial snapshot and registering the listener used to
     * observe new events, this remembers the highest sequence number already
     * present ({@code watermark}), registers the listener, then re-scans the
     * (possibly since-grown) journal for a match with a sequence number
     * above the watermark before waiting on the listener. {@link
     * XaEvent#sequence()} is strictly increasing, so this scan/wait
     * combination can neither miss nor double-report an event.
     *
     * @throws ProbeTimeoutException if no matching event arrives in time
     */
    public XaEvent awaitEvent(Predicate<XaEvent> predicate, Duration timeout) {
        List<XaEvent> snapshot = engine.journal().events();
        for (XaEvent event : snapshot) {
            if (predicate.test(event)) {
                return event;
            }
        }
        long watermark = snapshot.isEmpty() ? 0 : snapshot.get(snapshot.size() - 1).sequence();

        BlockingQueue<XaEvent> matches = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        XaEventListener listener = event -> {
            if (predicate.test(event)) {
                matches.offer(event);
            }
        };
        engine.addListener(listener);
        try {
            for (XaEvent event : engine.journal().events()) {
                if (event.sequence() > watermark && predicate.test(event)) {
                    return event;
                }
            }
            return awaitFromQueue(matches, watermark, timeout);
        } finally {
            engine.removeListener(listener);
        }
    }

    private XaEvent awaitFromQueue(BlockingQueue<XaEvent> matches, long watermark, Duration timeout) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new ProbeTimeoutException("No matching event arrived within " + timeout);
            }
            XaEvent candidate;
            try {
                candidate = matches.poll(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProbeTimeoutException("Interrupted while waiting for a matching event", e);
            }
            if (candidate == null) {
                throw new ProbeTimeoutException("No matching event arrived within " + timeout);
            }
            if (candidate.sequence() > watermark) {
                return candidate;
            }
        }
    }
}
