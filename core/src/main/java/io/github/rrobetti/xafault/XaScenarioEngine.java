package io.github.rrobetti.xafault;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.transaction.xa.XAException;

public final class XaScenarioEngine {
    private final XaEventJournal journal = new XaEventJournal();
    private final List<XaRule> rules = new CopyOnWriteArrayList<>();
    private final List<XaEventListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong operationOrdinal = new AtomicLong();

    public void addRule(XaRule rule) {
        rules.add(rule);
    }

    /**
     * Registers a listener that is notified, in append order, of every event
     * recorded from now on. Notification happens synchronously before rules
     * are applied, on the thread that performed the XA call.
     */
    public void addListener(XaEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeListener(XaEventListener listener) {
        listeners.remove(listener);
    }

    public long nextOperationOrdinal() {
        return operationOrdinal.incrementAndGet();
    }

    public XaEvent record(XaEvent event) throws XAException {
        XaEvent recorded = journal.append(event);
        for (XaEventListener listener : listeners) {
            listener.onEvent(recorded);
        }
        for (XaRule rule : rules) {
            rule.apply(recorded);
        }
        return recorded;
    }

    public XaEventJournal journal() {
        return journal;
    }
}
