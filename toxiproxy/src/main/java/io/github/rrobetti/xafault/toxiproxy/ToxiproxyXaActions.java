package io.github.rrobetti.xafault.toxiproxy;

import io.github.rrobetti.xafault.XaAction;

/**
 * Bridges {@link ToxiproxyProxy} operations into {@link XaAction}s, so a
 * {@code XaRule} can cut or degrade the real network path an XA resource
 * relies on instead of (or in addition to) throwing a synthetic
 * {@link javax.transaction.xa.XAException}.
 */
public final class ToxiproxyXaActions {
    private ToxiproxyXaActions() {}

    /** Disables {@code proxy}, simulating the upstream becoming unreachable, then lets the call proceed. */
    public static XaAction disable(ToxiproxyProxy proxy) {
        return XaAction.callback(event -> proxy.disable());
    }

    /** Re-enables {@code proxy}, then lets the call proceed. */
    public static XaAction enable(ToxiproxyProxy proxy) {
        return XaAction.callback(event -> proxy.enable());
    }

    /** Attaches {@code toxic} to {@code proxy}, then lets the call proceed. */
    public static XaAction addToxic(ToxiproxyProxy proxy, Toxic toxic) {
        return XaAction.callback(event -> proxy.addToxic(toxic));
    }
}
