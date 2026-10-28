package com.epam.java.specialization.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway is the first hop of a trace: it continues the client's {@code traceparent} (or starts a trace)
 * and forwards it, with its own span as the parent, to the service it routes to.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.rate-limit.enabled=false"
})
@AutoConfigureTracing
class GatewayTracePropagationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String CLIENT_SPAN_ID = "00f067aa0ba902b7";

    private static HttpServer catalog;
    private static final AtomicReference<String> received = new AtomicReference<>();

    @LocalServerPort
    private int port;

    @BeforeAll
    static void startStub() throws IOException {
        catalog = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        catalog.createContext("/api/v1/tracks/3", exchange -> {
            received.set(exchange.getRequestHeaders().getFirst("traceparent"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        catalog.start();
    }

    @AfterAll
    static void stopStub() {
        catalog.stop(0);
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + catalog.getAddress().getPort());
    }

    @Test
    void routedRequestContinuesTheClientsTrace() throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/tracks/3"))
                        .header("traceparent", "00-" + TRACE_ID + "-" + CLIENT_SPAN_ID + "-01")
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(received.get()).startsWith("00-" + TRACE_ID + "-")
                .doesNotContain(CLIENT_SPAN_ID);
    }
}
