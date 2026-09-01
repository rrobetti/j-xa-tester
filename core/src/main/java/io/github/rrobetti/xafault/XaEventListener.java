package io.github.rrobetti.xafault;

/**
 * Observes every {@link XaEvent} recorded by a {@link XaScenarioEngine}, in
 * the same order they are appended to the {@link XaEventJournal}. Listeners
 * run synchronously on the thread performing the XA call, so they must be
 * fast and must not themselves invoke back into the resource under test.
 *
 * <p>Used by the timeline module to implement blocking probes, and by the
 * JUnit 5 extension to notice failures as they happen, without polling the
 * journal.
 */
@FunctionalInterface
public interface XaEventListener {
    void onEvent(XaEvent event);
}
