package io.github.rrobetti.xafault.junit5;

import io.github.rrobetti.xafault.EventPosition;
import io.github.rrobetti.xafault.XaOperation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a synthetic XA fault that {@link XaScenarioExtension} registers as
 * an {@code XaRule} before the annotated test method runs: it matches calls
 * to {@code resourceId()} for {@code operation()} at {@code position()}, and
 * throws an {@code XAException} carrying {@code errorCode()} the first time
 * it matches.
 *
 * <p>Repeatable: annotate a method more than once (or with {@link XaFaults})
 * to register several independent faults on the same test.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(XaFaults.class)
public @interface XaFault {
    /** The {@code resourceId} the rule matches, as passed to {@code FaultInjectingXAResource}/adapters. */
    String resourceId();

    /** The XA operation the rule matches. */
    XaOperation operation();

    /** The point in the call at which the rule matches; defaults to just before the delegate call runs. */
    EventPosition position() default EventPosition.BEFORE;

    /** The {@code XAException.errorCode} the synthetic failure carries. */
    int errorCode();

    /**
     * When {@code true} (the default), {@link XaScenarioExtension} fails the
     * test during teardown if this fault's rule never fired, turning a fault
     * that silently never triggered into a hard failure instead of a
     * misleadingly green test.
     */
    boolean requireFired() default true;
}
