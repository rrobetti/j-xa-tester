package io.github.rrobetti.xafault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import javax.transaction.xa.XAException;
import org.junit.jupiter.api.Test;

class XaScenarioEngineListenerTest {
    @Test
    void listenersObserveEventsInJournalOrderBeforeRulesApply() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        List<EventPosition> observed = new ArrayList<>();
        engine.addListener(event -> observed.add(event.position()));
        TestSupport.RecordingResource delegate = new TestSupport.RecordingResource();
        FaultInjectingXAResource resource = new FaultInjectingXAResource(delegate, engine, "orders-db", ResourceKind.JDBC);

        resource.prepare(new TestSupport.TestXid());

        assertEquals(List.of(EventPosition.BEFORE, EventPosition.AFTER_SUCCESS), observed);
    }

    @Test
    void listenerSeesBeforeEventEvenWhenARuleInjectsAFailure() {
        XaScenarioEngine engine = new XaScenarioEngine();
        List<EventPosition> observed = new ArrayList<>();
        engine.addListener(event -> observed.add(event.position()));
        engine.addRule(new XaRule(XaRules.before("orders-db", XaOperation.COMMIT),
                XaAction.throwException(XAException.XAER_RMFAIL)));
        TestSupport.RecordingResource delegate = new TestSupport.RecordingResource();
        FaultInjectingXAResource resource = new FaultInjectingXAResource(delegate, engine, "orders-db", ResourceKind.JDBC);

        assertThrows(XAException.class, () -> resource.commit(new TestSupport.TestXid(), false));

        assertEquals(List.of(EventPosition.BEFORE, EventPosition.AFTER_FAILURE), observed);
    }

    @Test
    void removedListenerStopsReceivingEvents() throws Exception {
        XaScenarioEngine engine = new XaScenarioEngine();
        List<EventPosition> observed = new ArrayList<>();
        XaEventListener listener = event -> observed.add(event.position());
        engine.addListener(listener);
        TestSupport.RecordingResource delegate = new TestSupport.RecordingResource();
        FaultInjectingXAResource resource = new FaultInjectingXAResource(delegate, engine, "orders-db", ResourceKind.JDBC);

        resource.prepare(new TestSupport.TestXid());
        engine.removeListener(listener);
        resource.commit(new TestSupport.TestXid(), false);

        assertEquals(List.of(EventPosition.BEFORE, EventPosition.AFTER_SUCCESS), observed);
    }
}
