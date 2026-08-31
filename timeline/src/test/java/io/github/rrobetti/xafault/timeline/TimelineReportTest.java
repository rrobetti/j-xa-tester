package io.github.rrobetti.xafault.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rrobetti.xafault.EventPosition;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaError;
import io.github.rrobetti.xafault.XaEvent;
import io.github.rrobetti.xafault.XaOperation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimelineReportTest {

    private static XaEvent event(long sequence, String resourceId, XaOperation operation, EventPosition position,
            XaError error) {
        return new XaEvent(sequence, Instant.parse("2024-01-01T00:00:00Z").plusMillis(sequence), resourceId,
                ResourceKind.JDBC, "instance-1", operation, position, null, null, null, null, error, "main", sequence,
                sequence);
    }

    @Test
    void renderProducesAHeaderAndOneRowPerEvent() {
        List<XaEvent> events = List.of(
                event(1, "orders-db", XaOperation.START, EventPosition.BEFORE, null),
                event(2, "orders-db", XaOperation.START, EventPosition.AFTER_SUCCESS, null),
                event(3, "orders-db", XaOperation.COMMIT, EventPosition.AFTER_FAILURE,
                        new XaError(-3, "javax.transaction.xa.XAException")));

        String rendered = TimelineReport.render(events);
        String[] lines = rendered.lines().toArray(String[]::new);

        assertEquals(4, lines.length, "header + 3 rows");
        assertTrue(lines[0].contains("SEQ"));
        assertTrue(lines[1].contains("orders-db"));
        assertTrue(lines[3].contains("XAException"));
    }

    @Test
    void renderOfEmptyListIsJustTheHeader() {
        String rendered = TimelineReport.render(List.of());
        assertEquals(1, rendered.lines().count());
    }

    @Test
    void summarizeCountsEventsByPositionAndFailures() {
        List<XaEvent> events = List.of(
                event(1, "orders-db", XaOperation.START, EventPosition.BEFORE, null),
                event(2, "orders-db", XaOperation.START, EventPosition.AFTER_SUCCESS, null),
                event(3, "orders-db", XaOperation.COMMIT, EventPosition.BEFORE, null),
                event(4, "orders-db", XaOperation.COMMIT, EventPosition.AFTER_FAILURE, new XaError(-3, "err")));

        TimelineReport.Summary summary = TimelineReport.summarize(events);

        assertEquals(4, summary.totalEvents());
        assertEquals(1, summary.failureCount());
        assertEquals(2L, summary.countsByPosition().get(EventPosition.BEFORE));
        assertEquals(1L, summary.countsByPosition().get(EventPosition.AFTER_SUCCESS));
        assertEquals(1L, summary.countsByPosition().get(EventPosition.AFTER_FAILURE));
    }

    @Test
    void summarizeOfEmptyListHasZeroCounts() {
        TimelineReport.Summary summary = TimelineReport.summarize(List.of());
        assertEquals(0, summary.totalEvents());
        assertEquals(0, summary.failureCount());
        for (EventPosition position : EventPosition.values()) {
            assertEquals(0L, summary.countsByPosition().get(position));
        }
    }
}
