package io.github.rrobetti.xafault;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javax.transaction.xa.XAException;

public final class XaRule {
    private final Predicate<XaEvent> matcher;
    private final XaAction action;
    private final AtomicBoolean fired = new AtomicBoolean();

    public XaRule(Predicate<XaEvent> matcher, XaAction action) {
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.action = Objects.requireNonNull(action, "action");
    }

    void apply(XaEvent event) throws XAException {
        if (matcher.test(event) && fired.compareAndSet(false, true)) {
            action.execute(event);
        }
    }

    public boolean fired() {
        return fired.get();
    }
}
