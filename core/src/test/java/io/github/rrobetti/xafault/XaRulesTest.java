package io.github.rrobetti.xafault;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class XaRulesTest {
    @Test
    void resourceIdOperationAndPositionMatchIndependently() {
        XaEvent event = event("orders-db", XaOperation.COMMIT, EventPosition.BEFORE, false);

        assertTrue(XaRules.resourceId("orders-db").test(event));
        assertFalse(XaRules.resourceId("other").test(event));
        assertTrue(XaRules.operation(XaOperation.COMMIT).test(event));
        assertFalse(XaRules.operation(XaOperation.PREPARE).test(event));
        assertTrue(XaRules.position(EventPosition.BEFORE).test(event));
        assertFalse(XaRules.position(EventPosition.AFTER_SUCCESS).test(event));
        assertTrue(XaRules.onePhase(false).test(event));
        assertFalse(XaRules.onePhase(true).test(event));
    }

    @Test
    void resourceKindMatchesWrapperKind() {
        XaEvent jdbcEvent = event("orders-db", XaOperation.PREPARE, EventPosition.BEFORE, null);

        assertTrue(XaRules.resourceKind(ResourceKind.JDBC).test(jdbcEvent));
        assertFalse(XaRules.resourceKind(ResourceKind.JMS).test(jdbcEvent));
    }

    @Test
    void beforeAfterSuccessAndAfterFailureShorthandsComposeThreeMatchers() {
        XaEvent before = event("orders-db", XaOperation.PREPARE, EventPosition.BEFORE, null);
        XaEvent afterSuccess = event("orders-db", XaOperation.PREPARE, EventPosition.AFTER_SUCCESS, null);
        XaEvent afterFailure = event("orders-db", XaOperation.PREPARE, EventPosition.AFTER_FAILURE, null);

        assertTrue(XaRules.before("orders-db", XaOperation.PREPARE).test(before));
        assertFalse(XaRules.before("orders-db", XaOperation.PREPARE).test(afterSuccess));
        assertTrue(XaRules.afterSuccess("orders-db", XaOperation.PREPARE).test(afterSuccess));
        assertFalse(XaRules.afterSuccess("orders-db", XaOperation.PREPARE).test(afterFailure));
        assertTrue(XaRules.afterFailure("orders-db", XaOperation.PREPARE).test(afterFailure));
        assertFalse(XaRules.afterFailure("orders-db", XaOperation.PREPARE).test(before));
    }

    @Test
    void nthMatchesOnlyTheGivenOccurrence() {
        var matcher = XaRules.nth(2, XaRules.operation(XaOperation.COMMIT));

        assertFalse(matcher.test(event("a", XaOperation.COMMIT, EventPosition.BEFORE, false)));
        assertTrue(matcher.test(event("a", XaOperation.COMMIT, EventPosition.BEFORE, false)));
        assertFalse(matcher.test(event("a", XaOperation.COMMIT, EventPosition.BEFORE, false)));
    }

    @Test
    void nthRejectsNonPositiveOccurrence() {
        assertThrows(IllegalArgumentException.class, () -> XaRules.nth(0, e -> true));
    }

    private static XaEvent event(String resourceId, XaOperation operation, EventPosition position, Boolean onePhase) {
        return new XaEvent(1, Instant.now(), resourceId, ResourceKind.JDBC, "instance-1", operation, position,
                null, null, onePhase, null, null, "test-thread", 1, 1);
    }
}
