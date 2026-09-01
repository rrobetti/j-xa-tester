package io.github.rrobetti.xafault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class XaGateTest {
    @Test
    void arriveAndAwaitBlocksUntilReleased() throws Exception {
        XaGate gate = new XaGate();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            XaEvent event = event();
            CompletableFuture<Void> arrival = CompletableFuture.runAsync(
                    () -> gate.arriveAndAwait(event, Duration.ofSeconds(5)), executor);

            XaEvent observed = gate.awaitArrival(Duration.ofSeconds(5));
            assertEquals(event, observed);
            assertFalse(arrival.isDone(), "the gated thread must still be blocked");

            gate.release();
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> arrival.get(5, TimeUnit.SECONDS));
            assertTrue(gate.isReleased());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void awaitArrivalTimesOutWhenNothingArrives() {
        XaGate gate = new XaGate();
        assertThrows(XaGateTimeoutException.class, () -> gate.awaitArrival(Duration.ofMillis(50)));
    }

    @Test
    void arriveAndAwaitTimesOutWhenNeverReleased() {
        XaGate gate = new XaGate();
        assertThrows(XaGateTimeoutException.class, () -> gate.arriveAndAwait(event(), Duration.ofMillis(50)));
        assertTrue(gate.hasArrived());
        assertFalse(gate.isReleased());
    }

    private static XaEvent event() {
        return new XaEvent(1, Instant.now(), "orders-db", ResourceKind.JDBC, "instance-1", XaOperation.COMMIT,
                EventPosition.BEFORE, null, null, false, null, null, "test-thread", 1, 1);
    }
}
