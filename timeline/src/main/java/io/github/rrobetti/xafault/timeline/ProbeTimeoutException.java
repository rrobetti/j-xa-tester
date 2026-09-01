package io.github.rrobetti.xafault.timeline;

/** Raised by {@link TimelineProbe#awaitEvent} when no matching event arrives before the deadline. */
public final class ProbeTimeoutException extends RuntimeException {
    public ProbeTimeoutException(String message) {
        super(message);
    }

    public ProbeTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
