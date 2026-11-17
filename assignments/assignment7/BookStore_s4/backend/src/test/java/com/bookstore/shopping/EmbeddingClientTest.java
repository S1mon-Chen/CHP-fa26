package com.bookstore.shopping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class EmbeddingClientTest {
    final ObjectMapper json = new ObjectMapper();
    final AtomicReference<String> response = new AtomicReference<>();
    final AtomicReference<String> request = new AtomicReference<>();
    HttpServer server;
    EmbeddingClient client;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embed", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        client = new EmbeddingClient(json, "http://127.0.0.1:" + server.getAddress().getPort() + "/embed");
    }
    @AfterEach void stop() { server.stop(0); }

    String payload(String model, int dimensions, double value) {
        var node = json.createObjectNode().put("model", model);
        var vector = node.putArray("embeddings").addArray();
        for (int i = 0; i < dimensions; i++) vector.add(value);
        return node.toString();
    }

    @Test void sendsChineseInputAndQueryModeAndNormalizesRealHttpResponse() throws Exception {
        response.set(payload(EmbeddingClient.MODEL, 512, 2));
        var vectors = client.embed(List.of("拆封还能退吗？"), "query");
        assertThat(vectors).hasDimensions(1, 512);
        double norm = 0;
        for (double x : vectors[0]) norm += x * x;
        assertThat(norm).isCloseTo(1.0, within(1e-10));
        var sent = json.readTree(request.get());
        assertThat(sent.path("input").get(0).asText()).isEqualTo("拆封还能退吗？");
        assertThat(sent.path("mode").asText()).isEqualTo("query");
    }

    @Test void refusesModelMismatchWrongDimensionsAndZeroVectors() {
        for (String invalid : List.of(payload("other-model", 512, 2),
                payload(EmbeddingClient.MODEL, 3, 2), payload(EmbeddingClient.MODEL, 512, 0))) {
            response.set(invalid);
            assertThatThrownBy(() -> client.embed(List.of("退货"), "query")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void refusesMalformedJsonWrongCountAndNonNumericComponents() {
        for (String invalid : List.of("not json", "{\"model\":\"" + EmbeddingClient.MODEL + "\",\"embeddings\":[]}",
                payload(EmbeddingClient.MODEL, 512, 2).replaceFirst("2.0", "null"))) {
            response.set(invalid);
            assertThatThrownBy(() -> client.embed(List.of("退货"), "query")).isInstanceOf(IllegalStateException.class);
        }
    }
}
