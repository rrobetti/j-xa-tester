package io.github.rrobetti.xafault.toxiproxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToxiproxyClientTest {

    private FakeToxiproxyServer server;
    private ToxiproxyClient client;

    @BeforeEach
    void start() throws IOException {
        server = new FakeToxiproxyServer();
        client = new ToxiproxyClient(server.baseUrl());
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void createsAndFetchesAProxy() {
        ToxiproxyProxy created = client.createProxy("mysql", "localhost:23306", "localhost:3306");

        assertEquals("mysql", created.name());
        assertEquals("localhost:23306", created.listen());
        assertEquals("localhost:3306", created.upstream());
        assertTrue(created.enabled());

        ToxiproxyProxy fetched = client.getProxy("mysql");
        assertEquals("mysql", fetched.name());
        assertEquals("localhost:3306", fetched.upstream());
    }

    @Test
    void listsProxies() {
        client.createProxy("mysql", "localhost:23306", "localhost:3306");
        client.createProxy("redis", "localhost:26379", "localhost:6379");

        List<ToxiproxyProxy> proxies = client.listProxies();

        assertEquals(2, proxies.size());
        assertTrue(proxies.stream().anyMatch(p -> p.name().equals("mysql")));
        assertTrue(proxies.stream().anyMatch(p -> p.name().equals("redis")));
    }

    @Test
    void disablesAndEnablesAProxy() {
        ToxiproxyProxy proxy = client.createProxy("mysql", "localhost:23306", "localhost:3306");

        proxy.disable();
        assertFalse(proxy.enabled());
        assertFalse(client.getProxy("mysql").enabled());

        proxy.enable();
        assertTrue(proxy.enabled());
        assertTrue(client.getProxy("mysql").enabled());
    }

    @Test
    void addsListsAndRemovesToxics() {
        ToxiproxyProxy proxy = client.createProxy("mysql", "localhost:23306", "localhost:3306");

        Toxic latency = Toxic.latency("slow-down", Toxic.Stream.DOWNSTREAM, 250, 50);
        proxy.addToxic(latency);

        List<Toxic> toxics = proxy.toxics();
        assertEquals(1, toxics.size());
        assertEquals("slow-down", toxics.get(0).name());
        assertEquals("latency", toxics.get(0).type());
        assertEquals(250L, toxics.get(0).attributes().get("latency"));

        proxy.removeToxic("slow-down");
        assertTrue(proxy.toxics().isEmpty());
    }

    @Test
    void deletesAProxy() {
        client.createProxy("mysql", "localhost:23306", "localhost:3306");
        client.deleteProxy("mysql");

        assertTrue(client.listProxies().isEmpty());
        assertThrows(ToxiproxyException.class, () -> client.getProxy("mysql"));
    }

    @Test
    void resetAllReEnablesEveryProxy() {
        ToxiproxyProxy proxy = client.createProxy("mysql", "localhost:23306", "localhost:3306");
        proxy.disable();

        client.resetAll();

        assertTrue(client.getProxy("mysql").enabled());
    }

    @Test
    void surfacesHttpErrorsAsToxiproxyException() {
        ToxiproxyException exception = assertThrows(ToxiproxyException.class, () -> client.getProxy("missing"));
        assertTrue(exception.getMessage().contains("404"));
    }

    @Test
    void surfacesConnectionFailuresAsToxiproxyException() {
        ToxiproxyClient badClient = new ToxiproxyClient("http://127.0.0.1:1");
        assertThrows(ToxiproxyException.class, () -> badClient.getProxy("mysql"));
    }
}
