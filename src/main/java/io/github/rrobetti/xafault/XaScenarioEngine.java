package io.github.rrobetti.xafault;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.transaction.xa.XAException;

public final class XaScenarioEngine {
    private final XaEventJournal journal = new XaEventJournal();
    private final List<XaRule> rules = new CopyOnWriteArrayList<>();
    private final AtomicLong operationOrdinal = new AtomicLong();

    public void addRule(XaRule rule) {
        rules.add(rule);
    }

    public long nextOperationOrdinal() {
        return operationOrdinal.incrementAndGet();
    }

    public XaEvent record(XaEvent event) throws XAException {
        XaEvent recorded = journal.append(event);
        for (XaRule rule : rules) {
            rule.apply(recorded);
        }
        return recorded;
    }

    public XaEventJournal journal() {
        return journal;
    }
}
