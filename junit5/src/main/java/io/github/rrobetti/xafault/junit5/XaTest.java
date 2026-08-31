package io.github.rrobetti.xafault.junit5;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test class as an XA fault-injection scenario. {@link XaScenarioExtension}
 * provisions a fresh {@link io.github.rrobetti.xafault.XaScenarioEngine} for
 * each test method (injectable as a method parameter), auto-registers any
 * {@link XaFault} rules declared on the method, and fails the test in its
 * teardown if a fault marked {@code requireFired() == true} never triggered.
 *
 * <pre>{@code
 * @XaTest
 * class OrderServiceTest {
 *     @Test
 *     @XaFault(resourceId = "orders-db", operation = XaOperation.COMMIT, errorCode = XAException.XAER_RMFAIL)
 *     void retriesWhenCommitFails(XaScenarioEngine engine) {
 *         XAResource resource = new FaultInjectingXAResource(realResource, engine, "orders-db", ResourceKind.JDBC);
 *         // exercise code that uses `resource`; the COMMIT call will throw XAER_RMFAIL exactly once.
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(XaScenarioExtension.class)
public @interface XaTest {
}
