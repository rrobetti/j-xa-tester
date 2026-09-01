package io.github.rrobetti.xafault.toxiproxy;

/** Raised when the Toxiproxy HTTP API cannot be reached or returns an error status. */
public final class ToxiproxyException extends RuntimeException {
    public ToxiproxyException(String message) {
        super(message);
    }

    public ToxiproxyException(String message, Throwable cause) {
        super(message, cause);
    }
}
