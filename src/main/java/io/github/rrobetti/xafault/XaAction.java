package io.github.rrobetti.xafault;

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
}
