package io.github.kweheliye.frauddetection.ml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class MlClientTest {

    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private HttpServer server(int status, String json) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/score", exchange -> {
            hits.incrementAndGet();
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private MlClient clientFor(HttpServer server) {
        return new MlClient("http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(2));
    }

    private MlRequest request() {
        return new MlRequest(20000, 55.0, 30.0, null, true, 2, true, Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void sendsTheSnakeCaseJsonTheSidecarExpectsAndReadsTheScore() throws Exception {
        HttpServer server = server(200, "{\"score\":0.91,\"model_version\":\"v2\"}");
        try {
            var score = clientFor(server).score(request());

            assertThat(score.isPresent()).isTrue();
            assertThat(score.getAsDouble()).isEqualTo(0.91);
            String body = lastBody.get();
            for (String field : new String[]{"\"amount\":20000", "\"customer_avg_amount\":55", "\"seconds_since_last_txn\":30",
                    "\"distance_from_last_km\":null", "\"country_changed\":true", "\"txns_last_hour\":2",
                    "\"is_new_device\":true", "\"timestamp\":\"2026-01-01T10:00:00Z\""}) {
                assertThat(body.contains(field)).isTrue();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsNoScoreWhenTheSidecarFailsAndThenStopsCallingItForAWhile() throws Exception {
        HttpServer server = server(500, "{\"detail\":\"boom\"}");
        try {
            MlClient client = clientFor(server);

            assertThat(client.score(request()).isPresent()).isFalse();
            assertThat(client.score(request()).isPresent()).isFalse();
            assertThat(hits.get()).isEqualTo(1);   // second call was skipped during the pause
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsNoScoreWhenNothingIsListening() {
        MlClient client = new MlClient("http://localhost:1", Duration.ofMillis(500));
        assertThat(client.score(request()).isPresent()).isFalse();
    }

    @Test
    void returnsNoScoreWhenTheSidecarDoesNotRespondInTime() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        slow.createContext("/score", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            exchange.close();
        });
        slow.start();
        try {
            MlClient client = new MlClient("http://localhost:" + slow.getAddress().getPort(), Duration.ofMillis(300));
            long start = System.nanoTime();
            assertThat(client.score(request()).isPresent()).isFalse();
            assertThat((System.nanoTime() - start) / 1_000_000 < 1200).isTrue();   // gave up at the timeout
        } finally {
            slow.stop(0);
        }
    }
}
