package io.github.rrobetti.xafault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.transaction.xa.XAException;
import org.junit.jupiter.api.Test;

class XaActionTest {
    @Test
    void delaySleepsAtLeastTheRequestedDuration() throws Exception {
        long start = System.nanoTime();
        XaAction.delay(Duration.ofMillis(30)).execute(event());
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMillis >= 25, "expected at least ~30ms delay, was " + elapsedMillis + "ms");
    }

    @Test
    void callbackReceivesTheTriggeringEvent() throws Exception {
        List<XaEvent> seen = new ArrayList<>();
        XaEvent event = event();

        XaAction.callback(seen::add).execute(event);

        assertEquals(List.of(event), seen);
    }

    @Test
    void gateBlocksUntilReleasedByAnotherThread() throws Exception {
        XaGate gate = new XaGate();
        Thread releaser = new Thread(() -> {
            gate.awaitArrival(Duration.ofSeconds(5));
            gate.release();
        });
        releaser.start();
        try {
            XaAction.gate(gate, Duration.ofSeconds(5)).execute(event());
            assertTrue(gate.isReleased());
        } finally {
            releaser.join(5_000);
        }
    }

    @Test
    void composeRunsActionsInOrderAndStopsOnException() {
        List<String> order = new ArrayList<>();
        XaAction first = e -> order.add("first");
        XaAction throwing = XaAction.throwException(XAException.XAER_RMFAIL);
        XaAction third = e -> order.add("third");

        XAException exception = assertThrows(XAException.class,
                () -> XaAction.compose(first, throwing, third).execute(event()));

        assertEquals(XAException.XAER_RMFAIL, exception.errorCode);
        assertEquals(List.of("first"), order);
    }

    private static XaEvent event() {
        return new XaEvent(1, Instant.now(), "orders-db", ResourceKind.JDBC, "instance-1", XaOperation.COMMIT,
                EventPosition.BEFORE, null, null, false, null, null, "test-thread", 1, 1);
    }
}
