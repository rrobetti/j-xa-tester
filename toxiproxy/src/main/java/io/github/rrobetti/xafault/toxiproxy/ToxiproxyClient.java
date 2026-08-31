package io.github.rrobetti.xafault.toxiproxy;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal client for the <a href="https://github.com/Shopify/toxiproxy">Toxiproxy</a>
 * HTTP API, built only on {@link HttpClient} (JDK-bundled) and the module's
 * own hand-rolled {@link Json} codec so that using it never requires adding
 * a JSON library or the {@code toxiproxy-java} client to the main classpath.
 */
public final class ToxiproxyClient {
    private final HttpClient httpClient;
    private final URI baseUri;

    public ToxiproxyClient(String baseUrl) {
        this(URI.create(baseUrl), HttpClient.newHttpClient());
    }

    public ToxiproxyClient(URI baseUri, HttpClient httpClient) {
        String raw = baseUri.toString();
        this.baseUri = URI.create(raw.endsWith("/") ? raw : raw + "/");
        this.httpClient = httpClient;
    }

    public ToxiproxyProxy createProxy(String name, String listen, String upstream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("listen", listen);
        body.put("upstream", upstream);
        return proxyFromResponse(requestObject("POST", "proxies", body));
    }

    public ToxiproxyProxy getProxy(String name) {
        return proxyFromResponse(requestObject("GET", "proxies/" + encode(name), null));
    }

    public List<ToxiproxyProxy> listProxies() {
        Map<String, Object> response = requestObject("GET", "proxies", null);
        List<ToxiproxyProxy> proxies = new ArrayList<>();
        for (Object value : response.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> proxyBody = (Map<String, Object>) value;
            proxies.add(proxyFromResponse(proxyBody));
        }
        return proxies;
    }

    public void deleteProxy(String name) {
        request("DELETE", "proxies/" + encode(name), null);
    }

    /** Re-enables every proxy and removes every toxic, mirroring Toxiproxy's own {@code POST /reset}. */
    public void resetAll() {
        request("POST", "reset", null);
    }

    ToxiproxyProxy setEnabled(String name, boolean enabled) {
        Map<String, Object> body = Map.of("enabled", enabled);
        return proxyFromResponse(requestObject("POST", "proxies/" + encode(name), body));
    }

    Toxic addToxic(String proxyName, Toxic toxic) {
        Map<String, Object> response =
                requestObject("POST", "proxies/" + encode(proxyName) + "/toxics", toxic.toRequestBody());
        return Toxic.fromResponse(response);
    }

    void removeToxic(String proxyName, String toxicName) {
        request("DELETE", "proxies/" + encode(proxyName) + "/toxics/" + encode(toxicName), null);
    }

    List<Toxic> listToxics(String proxyName) {
        Object response = request("GET", "proxies/" + encode(proxyName) + "/toxics", null);
        List<Toxic> toxics = new ArrayList<>();
        if (response instanceof List<?> list) {
            for (Object item : list) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) item;
                toxics.add(Toxic.fromResponse(body));
            }
        }
        return toxics;
    }

    private ToxiproxyProxy proxyFromResponse(Map<String, Object> body) {
        String name = (String) body.get("name");
        String listen = (String) body.get("listen");
        String upstream = (String) body.get("upstream");
        boolean enabled = !Boolean.FALSE.equals(body.get("enabled"));
        return new ToxiproxyProxy(this, name, listen, upstream, enabled);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requestObject(String method, String path, Object body) {
        Object result = request(method, path, body);
        return result == null ? Map.of() : (Map<String, Object>) result;
    }

    private Object request(String method, String path, Object body) {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, publisher)
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ToxiproxyException("Failed to reach Toxiproxy API at " + baseUri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToxiproxyException("Interrupted while calling Toxiproxy API at " + baseUri, e);
        }
        if (response.statusCode() >= 400) {
            throw new ToxiproxyException("Toxiproxy API returned HTTP " + response.statusCode() + " for " + method
                    + " " + path + ": " + response.body());
        }
        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        return Json.read(responseBody);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
