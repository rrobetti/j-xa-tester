package io.github.rrobetti.xafault;

import javax.transaction.xa.XAException;

public record XaError(int code, String type) {
    static XaError from(XAException exception) {
        return new XaError(exception.errorCode, exception.getClass().getName());
    }
}
