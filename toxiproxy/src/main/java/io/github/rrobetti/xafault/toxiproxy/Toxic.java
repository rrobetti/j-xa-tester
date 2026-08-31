package io.github.rrobetti.xafault.toxiproxy;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * An immutable description of a Toxiproxy toxic: a named fault (latency,
 * timeout, bandwidth cap, ...) attached to one side of a proxied connection.
 * Use the static factories for the toxic types Toxiproxy ships with, or the
 * canonical constructor for any other type/attribute combination.
 */
public record Toxic(String name, String type, Stream stream, double toxicity, Map<String, Object> attributes) {

    public enum Stream {
        UPSTREAM,
        DOWNSTREAM;

        String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public Toxic {
        attributes = Map.copyOf(attributes);
    }

    /** Adds {@code latencyMillis} (+/- {@code jitterMillis}) of delay to every packet. */
    public static Toxic latency(String name, Stream stream, long latencyMillis, long jitterMillis) {
        return new Toxic(name, "latency", stream, 1.0, attrs("latency", latencyMillis, "jitter", jitterMillis));
    }

    /** Stops all data from getting through and closes the connection after {@code timeoutMillis} (0 = never closes). */
    public static Toxic timeout(String name, Stream stream, long timeoutMillis) {
        return new Toxic(name, "timeout", stream, 1.0, attrs("timeout", timeoutMillis));
    }

    /** Simulates a TCP RESET (ECONNRESET) after {@code timeoutMillis} of no activity. */
    public static Toxic resetPeer(String name, Stream stream, long timeoutMillis) {
        return new Toxic(name, "reset_peer", stream, 1.0, attrs("timeout", timeoutMillis));
    }

    /** Limits transfer to {@code rateKbps} KB/s. */
    public static Toxic bandwidth(String name, Stream stream, long rateKbps) {
        return new Toxic(name, "bandwidth", stream, 1.0, attrs("rate", rateKbps));
    }

    /** Delays the connection's close by {@code delayMillis} once no more data can be sent/received. */
    public static Toxic slowClose(String name, Stream stream, long delayMillis) {
        return new Toxic(name, "slow_close", stream, 1.0, attrs("delay", delayMillis));
    }

    /** Closes the connection after {@code bytes} have been transmitted. */
    public static Toxic limitData(String name, Stream stream, long bytes) {
        return new Toxic(name, "limit_data", stream, 1.0, attrs("bytes", bytes));
    }

    @SuppressWarnings("unchecked")
    static Toxic fromResponse(Map<String, Object> body) {
        String name = (String) body.get("name");
        String type = (String) body.get("type");
        Stream stream = Stream.valueOf(String.valueOf(body.get("stream")).toUpperCase(Locale.ROOT));
        Number toxicity = (Number) body.get("toxicity");
        Map<String, Object> attributes = (Map<String, Object>) body.getOrDefault("attributes", Map.of());
        return new Toxic(name, type, stream, toxicity == null ? 1.0 : toxicity.doubleValue(), attributes);
    }

    Map<String, Object> toRequestBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", type);
        body.put("stream", stream.wireName());
        body.put("toxicity", toxicity);
        body.put("attributes", attributes);
        return body;
    }

    private static Map<String, Object> attrs(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }
}
