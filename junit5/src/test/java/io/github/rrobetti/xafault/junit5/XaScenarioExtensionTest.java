package io.github.rrobetti.xafault.junit5;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import io.github.rrobetti.xafault.FaultInjectingXAResource;
import io.github.rrobetti.xafault.ResourceKind;
import io.github.rrobetti.xafault.XaOperation;
import io.github.rrobetti.xafault.XaScenarioEngine;
import java.util.List;
import java.util.Optional;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

/**
 * Drives {@link XaScenarioExtension} through {@link EngineTestKit}, running
 * the nested {@code @XaTest}-annotated fixture classes as real (isolated)
 * Jupiter executions and asserting on the resulting event stream, rather than
 * calling extension callback methods directly.
 */
class XaScenarioExtensionTest {

    @Test
    void resolvesAFreshEngineAndPassesWhenTheDeclaredFaultFires() {
        EngineExecutionResults results =
                EngineTestKit.engine("junit-jupiter").selectors(selectClass(FaultFiringScenario.class)).execute();

        results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    void failsDuringTeardownWhenARequiredFaultNeverFires() {
        EngineExecutionResults results =
                EngineTestKit.engine("junit-jupiter").selectors(selectClass(MissingFaultScenario.class)).execute();

        results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(0).failed(1));
        List<Throwable> failures = failureThrowables(results);
        assertTrue(failures.get(0) instanceof AssertionError);
        assertTrue(failures.get(0).getMessage().contains("never fired"));
    }

    @Test
    void aFaultMarkedRequireFiredFalseIsAllowedToNeverFire() {
        EngineExecutionResults results =
                EngineTestKit.engine("junit-jupiter").selectors(selectClass(OptionalFaultScenario.class)).execute();

        results.testEvents().assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }

    @Test
    void eachTestMethodGetsAnIndependentEngineWithNoCrossTalk() {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(TwoIndependentTestsScenario.class))
                .execute();

        results.testEvents().assertStatistics(stats -> stats.started(2).succeeded(2).failed(0));
    }

    private static List<Throwable> failureThrowables(EngineExecutionResults results) {
        return results.testEvents().failed().stream()
                .map(XaScenarioExtensionTest::throwableOf)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static Throwable throwableOf(Event event) {
        Optional<TestExecutionResult> result = event.getPayload(TestExecutionResult.class);
        return result.flatMap(TestExecutionResult::getThrowable).orElse(null);
    }

    @XaTest
    static class FaultFiringScenario {
        @Test
        @XaFault(resourceId = "orders-db", operation = XaOperation.COMMIT, errorCode = XAException.XAER_RMFAIL)
        void test(XaScenarioEngine engine) throws Exception {
            XAResource resource =
                    new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
            resource.start(new SimpleXid(1), XAResource.TMNOFLAGS);
            resource.end(new SimpleXid(1), XAResource.TMSUCCESS);
            resource.prepare(new SimpleXid(1));

            assertThrows(XAException.class, () -> resource.commit(new SimpleXid(1), false));
        }
    }

    @XaTest
    static class MissingFaultScenario {
        @Test
        @XaFault(resourceId = "orders-db", operation = XaOperation.COMMIT, errorCode = XAException.XAER_RMFAIL)
        void test(XaScenarioEngine engine) throws Exception {
            XAResource resource =
                    new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
            // Never calls commit(), so the declared fault can never fire; the extension must fail the test.
            resource.start(new SimpleXid(1), XAResource.TMNOFLAGS);
        }
    }

    @XaTest
    static class OptionalFaultScenario {
        @Test
        @XaFault(resourceId = "orders-db", operation = XaOperation.COMMIT, errorCode = XAException.XAER_RMFAIL,
                requireFired = false)
        void test(XaScenarioEngine engine) throws Exception {
            XAResource resource =
                    new FaultInjectingXAResource(new FakeXAResource(), engine, "orders-db", ResourceKind.JDBC);
            resource.start(new SimpleXid(1), XAResource.TMNOFLAGS);
        }
    }

    @XaTest
    static class TwoIndependentTestsScenario {
        @Test
        @XaFault(resourceId = "a", operation = XaOperation.START, errorCode = XAException.XAER_RMFAIL)
        void first(XaScenarioEngine engine) {
            XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "a", ResourceKind.JDBC);
            assertThrows(XAException.class, () -> resource.start(new SimpleXid(1), XAResource.TMNOFLAGS));
        }

        @Test
        void second(XaScenarioEngine engine) throws Exception {
            // No @XaFault here: if the engine were shared/stale, this identical call would also fail.
            XAResource resource = new FaultInjectingXAResource(new FakeXAResource(), engine, "a", ResourceKind.JDBC);
            resource.start(new SimpleXid(2), XAResource.TMNOFLAGS);
        }
    }
}
