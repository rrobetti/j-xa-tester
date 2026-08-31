package io.github.rrobetti.xafault;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.transaction.xa.XAException;

@FunctionalInterface
public interface XaAction {
    void execute(XaEvent event) throws XAException;

    static XaAction throwException(int errorCode) {
        return event -> {
            XAException exception = new XAException(errorCode);
            exception.errorCode = errorCode;
            throw exception;
        };
    }

    /**
     * Sleeps the calling thread for {@code duration}, then lets the call
     * proceed. Useful for simulating a slow resource manager without a real
     * network fault.
     */
    static XaAction delay(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        return event -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * Blocks the calling thread at {@code gate} until a coordinating thread
     * calls {@link XaGate#release()}. See {@link XaGate} for the intended
     * pattern of lining up an external fault with a precise XA call.
     */
    static XaAction gate(XaGate gate, Duration timeout) {
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(timeout, "timeout");
        return event -> gate.arriveAndAwait(event, timeout);
    }

    /**
     * Runs a side-effecting, non-throwing callback (for example disabling a
     * Toxiproxy proxy) and lets the call proceed. Combine with a real broken
     * resource so the delegate call fails on its own instead of relying on a
     * synthetic {@link #throwException(int)}.
     */
    static XaAction callback(Consumer<XaEvent> callback) {
        Objects.requireNonNull(callback, "callback");
        return callback::accept;
    }

    /** Runs each action in order; a thrown {@link XAException} stops the chain. */
    static XaAction compose(XaAction... actions) {
        List<XaAction> copy = List.of(actions);
        return event -> {
            for (XaAction action : copy) {
                action.execute(event);
            }
        };
    }
}
