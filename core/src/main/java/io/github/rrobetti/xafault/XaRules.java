package io.github.rrobetti.xafault;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Fluent {@link XaEvent} predicate factories for building {@link XaRule}
 * matchers without hand-rolling lambdas for every combination of resource,
 * operation, and position.
 */
public final class XaRules {
    private XaRules() {}

    public static Predicate<XaEvent> resourceId(String resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        return event -> resourceId.equals(event.resourceId());
    }

    public static Predicate<XaEvent> resourceKind(ResourceKind kind) {
        Objects.requireNonNull(kind, "kind");
        return event -> event.resourceKind() == kind;
    }

    public static Predicate<XaEvent> operation(XaOperation operation) {
        Objects.requireNonNull(operation, "operation");
        return event -> event.operation() == operation;
    }

    public static Predicate<XaEvent> position(EventPosition position) {
        Objects.requireNonNull(position, "position");
        return event -> event.position() == position;
    }

    public static Predicate<XaEvent> onePhase(boolean onePhase) {
        return event -> Boolean.valueOf(onePhase).equals(event.onePhase());
    }

    /** Matches the Nth (1-based) event that satisfies {@code matcher}, evaluated in journal order. */
    public static Predicate<XaEvent> nth(int n, Predicate<XaEvent> matcher) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be >= 1, was " + n);
        }
        Objects.requireNonNull(matcher, "matcher");
        AtomicInteger seen = new AtomicInteger();
        return event -> matcher.test(event) && seen.incrementAndGet() == n;
    }

    /** Shorthand for a BEFORE event on the given resource and operation. */
    public static Predicate<XaEvent> before(String resourceId, XaOperation operation) {
        return resourceId(resourceId).and(operation(operation)).and(position(EventPosition.BEFORE));
    }

    /** Shorthand for an AFTER_SUCCESS event on the given resource and operation. */
    public static Predicate<XaEvent> afterSuccess(String resourceId, XaOperation operation) {
        return resourceId(resourceId).and(operation(operation)).and(position(EventPosition.AFTER_SUCCESS));
    }

    /** Shorthand for an AFTER_FAILURE event on the given resource and operation. */
    public static Predicate<XaEvent> afterFailure(String resourceId, XaOperation operation) {
        return resourceId(resourceId).and(operation(operation)).and(position(EventPosition.AFTER_FAILURE));
    }
}
