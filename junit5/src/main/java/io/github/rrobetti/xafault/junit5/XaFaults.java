package io.github.rrobetti.xafault.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Container annotation enabling {@link XaFault} to be repeated on a single test method. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface XaFaults {
    XaFault[] value();
}
