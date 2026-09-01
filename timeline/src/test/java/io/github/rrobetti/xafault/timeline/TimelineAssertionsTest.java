package io.github.rrobetti.xafault.timeline;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaAction;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaRule;
import io.github.rrobetti.xafault.XaRules;
import io.github.rrobetti.xafault.XaScenarioEngine;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import org.junit.jupiter.api.Test;

class TimelineAssertionsTest {

    @Test
    void assertNoFailuresPassesWhenThereAreNone() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
        resource.start(new SimpleXid(1), XAResource.TMNOFLAGS);

        assertDoesNotThrow(() -> TimelineAssertions.assertNoFailures(engine.journal().events()));
    }

    @Test
    void assertNoFailuresFailsWhenARuleInjectsAFailure() {
        XaScenarioEngine engine = new XaScenarioEngine();
        engine.addRule(new XaRule(XaRules.before("orders-db", XaOperation.START),
                XaAction.throwException(XAException.XAER_RMFAIL)));
        XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);

        assertThrows(XAException.class, () -> resource.start(new SimpleXid(1), XAResource.TMNOFLAGS));
        AssertionError error =
                assertThrows(AssertionError.class, () -> TimelineAssertions.assertNoFailures(engine.journal().events()));
        assertTrue(error.getMessage().contains("Expected no failures"));
    }

    @Test
    void assertOperationOrderPassesForTheExactSequence() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
        resource.start(new SimpleXid(1), XAResource.TMNOFLAGS);
        resource.end(new SimpleXid(1), XAResource.TMSUCCESS);
        resource.prepare(new SimpleXid(1));
        resource.commit(new SimpleXid(1), false);

        assertDoesNotThrow(() -> TimelineAssertions.assertOperationOrder(engine.journal().events(), "orders-db",
                XaOperation.START, XaOperation.END, XaOperation.PREPARE, XaOperation.COMMIT));
    }

    @Test
    void assertOperationOrderFailsForAWrongSequence() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
        resource.start(new SimpleXid(1), XAResource.TMNOFLAGS);
        resource.end(new SimpleXid(1), XAResource.TMSUCCESS);

        assertThrows(AssertionError.class, () -> TimelineAssertions.assertOperationOrder(engine.journal().events(),
                "orders-db", XaOperation.END, XaOperation.START));
    }

    @Test
    void assertFiredPassesOnlyAfterTheRuleHasFired() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        XaRule rule = new XaRule(XaRules.before("orders-db", XaOperation.START),
                XaAction.throwException(XAException.XAER_RMFAIL));
        engine.addRule(rule);

        assertThrows(AssertionError.class, () -> TimelineAssertions.assertFired(rule, "start should fail"));

        XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
        assertThrows(XAException.class, () -> resource.start(new SimpleXid(1), XAResource.TMNOFLAGS));

        assertDoesNotThrow(() -> TimelineAssertions.assertFired(rule, "start should fail"));
    }
}
