package io.github.rrobetti.xafault.junit5;

import io.github.rrobetti.xafault.XaAction;
import io.github.rrobetti.xafault.XaEvent;
import io.github.rrobetti.xafault.XaRule;
import io.github.rrobetti.xafault.XaRules;
import io.github.rrobetti.xafault.XaScenarioEngine;
import io.github.rrobetti.xafault.timeline.TimelineReport;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * The JUnit 5 extension backing {@link XaTest}. For each test method it
 * provisions a fresh {@link XaScenarioEngine} (resolvable as a method
 * parameter), registers one {@link XaRule} per {@link XaFault} annotation on
 * the method, and in teardown fails the test if any fault marked {@code
 * requireFired() == true} never fired, dumping the recorded timeline to
 * {@code stderr} whenever the test itself failed.
 */
public final class XaScenarioExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(XaScenarioExtension.class);
    private static final String ENGINE_KEY = "engine";
    private static final String RULES_KEY = "rules";

    @Override
    public void beforeEach(ExtensionContext context) {
        XaScenarioEngine engine = new XaScenarioEngine();
        context.getStore(NAMESPACE).put(ENGINE_KEY, engine);

        List<TrackedFault> tracked = new ArrayList<>();
        Method testMethod = context.getRequiredTestMethod();
        for (XaFault fault : testMethod.getAnnotationsByType(XaFault.class)) {
            XaRule rule = new XaRule(matcherFor(fault), XaAction.throwException(fault.errorCode()));
            engine.addRule(rule);
            tracked.add(new TrackedFault(rule, fault));
        }
        context.getStore(NAMESPACE).put(RULES_KEY, tracked);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        XaScenarioEngine engine = engineFrom(context);
        List<TrackedFault> tracked = trackedFaultsFrom(context);

        if (context.getExecutionException().isPresent()) {
            System.err.println("XA timeline for failed test " + context.getDisplayName() + ":");
            System.err.println(TimelineReport.render(engine.journal().events()));
        }

        List<String> neverFired = new ArrayList<>();
        for (TrackedFault entry : tracked) {
            if (entry.fault.requireFired() && !entry.rule.fired()) {
                neverFired.add(describe(entry.fault));
            }
        }
        if (!neverFired.isEmpty()) {
            throw new AssertionError("The following @XaFault rule(s) never fired: " + neverFired);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == XaScenarioEngine.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return engineFrom(extensionContext);
    }

    private static XaScenarioEngine engineFrom(ExtensionContext context) {
        XaScenarioEngine engine = context.getStore(NAMESPACE).get(ENGINE_KEY, XaScenarioEngine.class);
        if (engine == null) {
            throw new ParameterResolutionException(
                    "No XaScenarioEngine registered for this test; is the test class annotated with @XaTest?");
        }
        return engine;
    }

    @SuppressWarnings("unchecked")
    private static List<TrackedFault> trackedFaultsFrom(ExtensionContext context) {
        List<TrackedFault> tracked = (List<TrackedFault>) context.getStore(NAMESPACE).get(RULES_KEY);
        return tracked == null ? List.of() : tracked;
    }

    private static Predicate<XaEvent> matcherFor(XaFault fault) {
        return XaRules.resourceId(fault.resourceId())
                .and(XaRules.operation(fault.operation()))
                .and(XaRules.position(fault.position()));
    }

    private static String describe(XaFault fault) {
        return fault.resourceId() + "/" + fault.operation() + "/" + fault.position() + " (errorCode=" + fault.errorCode()
                + ")";
    }

    private record TrackedFault(XaRule rule, XaFault fault) {
    }
}
