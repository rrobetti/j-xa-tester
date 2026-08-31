package io.github.rrobetti.xafault.timeline;

import io.github.rrobetti.xafault.EventPosition;
import io.github.rrobetti.xafault.XaEvent;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaRule;
import java.util.List;

/**
 * Dependency-free assertion helpers over a recorded {@link XaEvent} timeline,
 * usable from any test framework (or none) since they throw a plain
 * {@link AssertionError} rather than depending on JUnit or Hamcrest.
 */
public final class TimelineAssertions {
    private TimelineAssertions() {}

    /** Fails if any event in {@code events} is at {@link EventPosition#AFTER_FAILURE}. */
    public static void assertNoFailures(List<XaEvent> events) {
        List<XaEvent> failures = events.stream().filter(e -> e.position() == EventPosition.AFTER_FAILURE).toList();
        if (!failures.isEmpty()) {
            throw new AssertionError("Expected no failures but found " + failures.size() + ":" + System.lineSeparator()
                    + TimelineReport.render(failures));
        }
    }

    /**
     * Fails unless the {@link EventPosition#BEFORE} events belonging to
     * {@code resourceId} carry exactly {@code expectedOrder} as their
     * operations, in that order (events for other resources are ignored,
     * and each call contributes exactly one {@code BEFORE} event regardless
     * of whether it later succeeds or fails).
     */
    public static void assertOperationOrder(List<XaEvent> events, String resourceId, XaOperation... expectedOrder) {
        List<XaOperation> actual = events.stream()
                .filter(e -> e.resourceId().equals(resourceId) && e.position() == EventPosition.BEFORE)
                .map(XaEvent::operation)
                .toList();
        List<XaOperation> expected = List.of(expectedOrder);
        if (!actual.equals(expected)) {
            throw new AssertionError(
                    "Expected operation order " + expected + " for resource '" + resourceId + "' but observed " + actual);
        }
    }

    /** Fails unless {@code rule} has fired at least once. */
    public static void assertFired(XaRule rule, String description) {
        if (!rule.fired()) {
            throw new AssertionError("Expected rule to have fired: " + description);
        }
    }
}
