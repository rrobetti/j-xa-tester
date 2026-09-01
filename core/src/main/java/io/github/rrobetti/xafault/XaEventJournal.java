package io.github.rrobetti.xafault;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class XaEventJournal {
    private final AtomicLong sequence = new AtomicLong();
    private final List<XaEvent> events = new CopyOnWriteArrayList<>();

    public XaEvent append(XaEvent event) {
        XaEvent sequenced = new XaEvent(sequence.incrementAndGet(), event.time(), event.resourceId(), event.resourceKind(),
                event.resourceInstanceId(), event.operation(), event.position(), event.xid(), event.flags(), event.onePhase(),
                event.returnCode(), event.error(), event.threadName(), event.operationOrdinal(), event.resourceOperationOrdinal());
        events.add(sequenced);
        return sequenced;
    }

    public List<XaEvent> events() {
        return List.copyOf(events);
    }
}
