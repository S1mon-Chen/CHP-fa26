package com.bookstore.shopping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

/** All documents and queries use the same local, pretrained Chinese model. */
@Component
public class EmbeddingClient {
    public static final String MODEL = "BAAI/bge-small-zh-v1.5";
    public static final int DIMENSIONS = 512;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public EmbeddingClient(ObjectMapper mapper,
                           @Value("${shopping.embedding-url:${EMBEDDING_URL:http://127.0.0.1:8092/embed}}") String endpoint) {
        this.mapper = mapper;
        this.endpoint = endpoint;
    }

    public double[][] embed(List<String> texts, String mode) {
        try {
            var body = mapper.createObjectNode().put("mode", mode);
            body.set("input", mapper.valueToTree(texts));
            var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("embedding HTTP failure");
            var root = mapper.readTree(response.body());
            var values = root.path("embeddings");
            if (!MODEL.equals(root.path("model").asText()) || !values.isArray()
                    || values.size() != texts.size()) throw new IllegalStateException("embedding model/count mismatch");
            double[][] result = new double[texts.size()][DIMENSIONS];
            for (int i = 0; i < result.length; i++) {
                var row = values.get(i);
                if (!row.isArray() || row.size() != DIMENSIONS) throw new IllegalStateException("embedding dimensions mismatch");
                double norm = 0;
                for (int j = 0; j < DIMENSIONS; j++) {
                    if (!row.get(j).isNumber()) throw new IllegalStateException("embedding is not numeric");
                    double value = row.get(j).asDouble();
                    if (!Double.isFinite(value)) throw new IllegalStateException("embedding is not finite");
                    result[i][j] = value;
                    norm += value * value;
                }
                if (!Double.isFinite(norm) || norm <= 0) throw new IllegalStateException("invalid embedding norm");
                for (int j = 0; j < DIMENSIONS; j++) result[i][j] /= Math.sqrt(norm);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("embedding service unavailable", e);
        }
    }
}
