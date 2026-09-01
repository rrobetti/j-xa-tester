package io.github.rrobetti.xafault;

/**
 * Thrown by {@link XaGate} when an arrival or release does not happen within
 * the requested timeout, or the waiting thread is interrupted.
 */
public final class XaGateTimeoutException extends RuntimeException {
    public XaGateTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
