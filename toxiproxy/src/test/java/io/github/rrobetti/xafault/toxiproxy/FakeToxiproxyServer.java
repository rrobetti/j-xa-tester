package io.github.rrobetti.xafault.toxiproxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An in-process fake of the slice of the Toxiproxy HTTP API this module
 * talks to, backed by {@link HttpServer} (JDK-bundled). Using a real
 * in-memory HTTP server instead of a Toxiproxy/Docker instance keeps the
 * client's tests deterministic and offline-friendly while still exercising
 * genuine HTTP request/response handling and JSON (de)serialization.
 */
final class FakeToxiproxyServer implements AutoCloseable {
    private static final Pattern TOXIC_PATH = Pattern.compile("/proxies/([^/]+)/toxics/([^/]+)");
    private static final Pattern TOXICS_PATH = Pattern.compile("/proxies/([^/]+)/toxics");
    private static final Pattern PROXY_PATH = Pattern.compile("/proxies/([^/]+)");

    private final HttpServer server;
    private final Map<String, Map<String, Object>> proxies = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> toxics = new ConcurrentHashMap<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    FakeToxiproxyServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    int requestCount() {
        return requestCount.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        try {
            route(exchange);
        } catch (ApiError e) {
            respond(exchange, e.status, Map.of("error", e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/reset") && method.equals("POST")) {
            proxies.values().forEach(p -> p.put("enabled", true));
            toxics.values().forEach(Map::clear);
            respond(exchange, 200, Map.of());
            return;
        }
        if (path.equals("/proxies") && method.equals("GET")) {
            respond(exchange, 200, new LinkedHashMap<>(proxies));
            return;
        }
        if (path.equals("/proxies") && method.equals("POST")) {
            createProxy(exchange);
            return;
        }
        Matcher toxicMatcher = TOXIC_PATH.matcher(path);
        if (toxicMatcher.matches()) {
            handleToxic(exchange, method, toxicMatcher.group(1), toxicMatcher.group(2));
            return;
        }
        Matcher toxicsMatcher = TOXICS_PATH.matcher(path);
        if (toxicsMatcher.matches()) {
            handleToxics(exchange, method, toxicsMatcher.group(1));
            return;
        }
        Matcher proxyMatcher = PROXY_PATH.matcher(path);
        if (proxyMatcher.matches()) {
            handleProxy(exchange, method, proxyMatcher.group(1));
            return;
        }
        throw new ApiError(404, "no route for " + method + " " + path);
    }

    private void createProxy(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        String name = (String) body.get("name");
        if (proxies.containsKey(name)) {
            throw new ApiError(409, "proxy already exists: " + name);
        }
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("name", name);
        proxy.put("listen", body.get("listen"));
        proxy.put("upstream", body.get("upstream"));
        proxy.put("enabled", body.getOrDefault("enabled", true));
        proxies.put(name, proxy);
        toxics.put(name, new ConcurrentHashMap<>());
        respond(exchange, 201, proxy);
    }

    private void handleProxy(HttpExchange exchange, String method, String name) throws IOException {
        Map<String, Object> proxy = proxies.get(name);
        if (proxy == null) {
            throw new ApiError(404, "no proxy named " + name);
        }
        switch (method) {
            case "GET" -> respond(exchange, 200, proxy);
            case "POST" -> {
                Map<String, Object> body = readBody(exchange);
                if (body.containsKey("enabled")) {
                    proxy.put("enabled", body.get("enabled"));
                }
                respond(exchange, 200, proxy);
            }
            case "DELETE" -> {
                proxies.remove(name);
                toxics.remove(name);
                respond(exchange, 204, null);
            }
            default -> throw new ApiError(405, "unsupported method " + method);
        }
    }

    private void handleToxics(HttpExchange exchange, String method, String proxyName) throws IOException {
        Map<String, Map<String, Object>> proxyToxics = toxics.get(proxyName);
        if (proxyToxics == null) {
            throw new ApiError(404, "no proxy named " + proxyName);
        }
        switch (method) {
            case "GET" -> respond(exchange, 200, new ArrayList<>(proxyToxics.values()));
            case "POST" -> {
                Map<String, Object> body = readBody(exchange);
                String name = (String) body.get("name");
                proxyToxics.put(name, body);
                respond(exchange, 200, body);
            }
            default -> throw new ApiError(405, "unsupported method " + method);
        }
    }

    private void handleToxic(HttpExchange exchange, String method, String proxyName, String toxicName)
            throws IOException {
        Map<String, Map<String, Object>> proxyToxics = toxics.get(proxyName);
        if (proxyToxics == null) {
            throw new ApiError(404, "no proxy named " + proxyName);
        }
        switch (method) {
            case "DELETE" -> {
                proxyToxics.remove(toxicName);
                respond(exchange, 204, null);
            }
            case "GET" -> {
                Map<String, Object> toxic = proxyToxics.get(toxicName);
                if (toxic == null) {
                    throw new ApiError(404, "no toxic named " + toxicName);
                }
                respond(exchange, 200, toxic);
            }
            default -> throw new ApiError(405, "unsupported method " + method);
        }
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        String raw = readString(exchange.getRequestBody());
        if (raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) Json.read(raw);
        return body;
    }

    private static String readString(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final class ApiError extends RuntimeException {
        final int status;

        ApiError(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
