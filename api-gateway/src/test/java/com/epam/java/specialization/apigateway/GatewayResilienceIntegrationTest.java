package com.epam.java.specialization.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the gateway against a stub catalog-service and checks timeouts, the circuit breaker fallback
 * and the correlation id end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.http.clients.read-timeout=500ms",
        "gateway.rate-limit.enabled=false"
})
class GatewayResilienceIntegrationTest {

    private static final String CORRELATION_ID = "0b6f7c1e-3d2a-4c8e-9f10-2a3b4c5d6e7f";

    private static HttpServer catalog;
    private static int closedPort;

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startStubs() throws IOException {
        catalog = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        catalog.createContext("/api/v1/tracks/1", exchange -> {
            // Echo the header like the real services do, to check the client still gets a single value.
            exchange.getResponseHeaders().add("X-Correlation-Id",
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id"));
            byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        catalog.createContext("/api/v1/tracks/2", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        catalog.start();

        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
    }

    @AfterAll
    static void stopStubs() {
        catalog.stop(0);
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + catalog.getAddress().getPort());
        registry.add("AUTH_SERVICE_URL", () -> "http://localhost:" + closedPort);
    }

    @Test
    void proxiesRequestAndKeepsSingleCorrelationId() throws Exception {
        HttpResponse<String> response = get("/api/v1/tracks/1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"id\":1}");
        assertThat(response.headers().allValues("X-Correlation-Id")).containsExactly(CORRELATION_ID);
    }

    @Test
    void slowServiceGetsGatewayTimeout() throws Exception {
        HttpResponse<String> response = get("/api/v1/tracks/2");

        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"SERVICE_TIMEOUT\"");
        assertThat(response.headers().allValues("X-Correlation-Id")).containsExactly(CORRELATION_ID);
    }

    @Test
    void unreachableServiceGetsServiceUnavailable() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    }

    @Test
    void gatewayOwnErrorsCarryCorrelationId() throws Exception {
        HttpResponse<String> response = get("/api/v1/users/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("X-Correlation-Id")).containsExactly(CORRELATION_ID);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).header("X-Correlation-Id", CORRELATION_ID).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
