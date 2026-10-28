package com.bookstore.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

@Component
public class ModelClient {
    private final ObjectMapper mapper;
    private final AssistantSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ModelClient(ObjectMapper mapper, AssistantSettings settings) {
        this.mapper = mapper;
        this.settings = settings;
    }

    public ObjectNode complete(ArrayNode messages, JsonNode tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", settings.getModel());
        body.put("stream", false);
        body.put("tool_choice", "auto");
        body.set("messages", messages);
        body.set("tools", tools);
        try {
            String endpoint = settings.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
            var request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (!settings.getApiKey().isBlank()) {
                request.header("Authorization", "Bearer " + settings.getApiKey());
            }
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // Do not relay upstream bodies: they may contain credentials or infrastructure details.
                throw new AssistantFailure(HttpStatus.BAD_GATEWAY,
                        "模型 API 返回 HTTP " + response.statusCode() + "，请检查模型、地址、密钥或额度。");
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode choice = root.path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asText())) {
                throw new AssistantFailure(HttpStatus.BAD_GATEWAY, "模型输出被截断，请缩短问题后重试。");
            }
            JsonNode message = choice.path("message");
            if (!message.isObject() || !"assistant".equals(message.path("role").asText())) {
                throw new AssistantFailure(HttpStatus.BAD_GATEWAY, "模型 API 返回了无效的消息格式。");
            }
            return (ObjectNode) message;
        } catch (HttpTimeoutException e) {
            throw new AssistantFailure(HttpStatus.GATEWAY_TIMEOUT, "模型 API 请求超时，请稍后重试。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssistantFailure(HttpStatus.SERVICE_UNAVAILABLE, "请求已中断，请重试。");
        } catch (IOException e) {
            throw new AssistantFailure(HttpStatus.BAD_GATEWAY, "无法连接模型 API 或响应不是合法 JSON，请检查配置。");
        }
    }
}
