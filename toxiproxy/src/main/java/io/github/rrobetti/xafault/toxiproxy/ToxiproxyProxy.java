package io.github.rrobetti.xafault.toxiproxy;

import java.util.List;

/**
 * A fluent, mutable handle to one proxy managed by a {@link ToxiproxyClient}.
 * Instances are only created by the client (from {@code createProxy}/{@code
 * getProxy}/{@code listProxies}) so that {@link #name()} always reflects a
 * proxy that existed at the time it was fetched.
 */
public final class ToxiproxyProxy {
    private final ToxiproxyClient client;
    private final String name;
    private final String listen;
    private final String upstream;
    private boolean enabled;

    ToxiproxyProxy(ToxiproxyClient client, String name, String listen, String upstream, boolean enabled) {
        this.client = client;
        this.name = name;
        this.listen = listen;
        this.upstream = upstream;
        this.enabled = enabled;
    }

    public String name() {
        return name;
    }

    public String listen() {
        return listen;
    }

    public String upstream() {
        return upstream;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Cuts the proxied connection, simulating the upstream becoming unreachable. */
    public ToxiproxyProxy disable() {
        enabled = client.setEnabled(name, false).enabled();
        return this;
    }

    /** Restores the proxied connection. */
    public ToxiproxyProxy enable() {
        enabled = client.setEnabled(name, true).enabled();
        return this;
    }

    public Toxic addToxic(Toxic toxic) {
        return client.addToxic(name, toxic);
    }

    public void removeToxic(String toxicName) {
        client.removeToxic(name, toxicName);
    }

    public List<Toxic> toxics() {
        return client.listToxics(name);
    }

    public void delete() {
        client.deleteProxy(name);
    }
}
