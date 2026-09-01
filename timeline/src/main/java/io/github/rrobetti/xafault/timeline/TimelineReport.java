package io.github.rrobetti.xafault.timeline;

import io.github.rrobetti.xafault.EventPosition;
import io.github.rrobetti.xafault.XaEvent;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a recorded {@link XaEvent} sequence as a fixed-width, human-readable
 * table for test failure diagnostics, and computes simple aggregate counts.
 */
public final class TimelineReport {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final String ROW_FORMAT = "%-5s %-13s %-14s %-6s %-11s %-13s %-6s %s%n";

    private TimelineReport() {}

    /** Renders {@code events} (in their given order) as a fixed-width table. */
    public static String render(List<XaEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(ROW_FORMAT, "SEQ", "TIME", "RESOURCE", "KIND", "OPERATION", "POSITION", "RC", "ERROR"));
        for (XaEvent event : events) {
            sb.append(String.format(ROW_FORMAT,
                    event.sequence(),
                    TIME_FORMAT.format(event.time()),
                    event.resourceId(),
                    event.resourceKind(),
                    event.operation(),
                    event.position(),
                    event.returnCode() == null ? "" : event.returnCode(),
                    event.error() == null ? "" : event.error().type() + "(" + event.error().code() + ")"));
        }
        return sb.toString();
    }

    /** Computes per-{@link EventPosition} counts and the overall failure count for {@code events}. */
    public static Summary summarize(List<XaEvent> events) {
        Map<EventPosition, Long> counts = new EnumMap<>(EventPosition.class);
        for (EventPosition position : EventPosition.values()) {
            counts.put(position, 0L);
        }
        for (XaEvent event : events) {
            counts.merge(event.position(), 1L, Long::sum);
        }
        long failureCount = counts.get(EventPosition.AFTER_FAILURE);
        return new Summary(events.size(), failureCount, Map.copyOf(counts));
    }

    /** Aggregate counts over a timeline: total events, failures, and a breakdown by {@link EventPosition}. */
    public record Summary(long totalEvents, long failureCount, Map<EventPosition, Long> countsByPosition) {
    }
}
