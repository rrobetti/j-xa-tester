package io.github.rrobetti.xafault.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.time.Duration;
import javax.transaction.xa.XAResource;
import org.junit.jupiter.api.Test;

class TimelineProbeTest {

    @Test
    void findsAnEventThatAlreadyHappenedBeforeTheProbeWasStarted() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
        resource.start(new SimpleXid(1), XAResource.TMNOFLAGS);

        TimelineProbe probe = new TimelineProbe(engine);
        var event = probe.awaitEvent(e -> e.operation() == XaOperation.START, Duration.ofSeconds(1));

        assertEquals(XaOperation.START, event.operation());
    }

    @Test
    void blocksUntilABackgroundThreadRecordsAMatchingEvent() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
        TimelineProbe probe = new TimelineProbe(engine);

        Thread background = new Thread(() -> {
            try {
                Thread.sleep(150);
                resource.start(new SimpleXid(2), XAResource.TMNOFLAGS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        background.start();
        try {
            var event = probe.awaitEvent(e -> e.operation() == XaOperation.START, Duration.ofSeconds(5));
            assertEquals(XaOperation.START, event.operation());
        } finally {
            background.join();
        }
    }

    @Test
    void timesOutWhenNoMatchingEventArrives() {
        XaScenarioEngine engine = new XaScenarioEngine();
        TimelineProbe probe = new TimelineProbe(engine);

        assertThrows(ProbeTimeoutException.class,
                () -> probe.awaitEvent(e -> e.operation() == XaOperation.COMMIT, Duration.ofMillis(200)));
    }

    @Test
    void doesNotMissAnEventThatRacesWithListenerRegistration() throws Exception {
        // Starts the background call with no artificial delay so it can land at any point relative to the
        // probe's internal snapshot/listener-registration sequence, repeating to exercise that narrow window.
        for (int i = 0; i < 20; i++) {
            XaScenarioEngine engine = new XaScenarioEngine();
            XAResource resource =
                    new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
            TimelineProbe probe = new TimelineProbe(engine);

            int formatId = i;
            Thread background = new Thread(() -> {
                try {
                    resource.start(new SimpleXid(formatId), XAResource.TMNOFLAGS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            background.start();
            try {
                var event = probe.awaitEvent(e -> e.operation() == XaOperation.START, Duration.ofSeconds(5));
                assertEquals(XaOperation.START, event.operation());
            } finally {
                background.join();
            }
        }
    }
}
