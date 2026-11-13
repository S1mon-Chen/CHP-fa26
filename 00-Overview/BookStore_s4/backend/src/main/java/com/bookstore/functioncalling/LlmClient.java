package com.bookstore.functioncalling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 调用 OpenAI 兼容的 /chat/completions 接口（默认 DeepSeek）
 * API Key 从环境变量 LLM_API_KEY 读取，不写入代码或配置文件。
 */
@Component
public class LlmClient {

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration requestTimeout;

    @Autowired
    public LlmClient(ObjectMapper mapper,
                     @Value("${llm.base-url:https://api.deepseek.com}") String baseUrl,
                     @Value("${llm.model:deepseek-flash}") String model,
                     @Value("${LLM_API_KEY:}") String apiKey,
                     @Value("${llm.timeout-seconds:60}") long timeoutSeconds) {
        this.mapper = mapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.apiKey = apiKey;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * 发送一轮对话
     * @param messages 完整的消息上下文
     * @param tools 可用的函数列表
     * @param allowTools false 时设置 tool_choice=none，强制模型直接给出文字回答
     * @return 模型返回的 assistant message（choices[0].message）
     */
    public ObjectNode chat(ArrayNode messages, ArrayNode tools, boolean allowTools) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("未配置环境变量 LLM_API_KEY，无法调用大模型");
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        body.set("tools", tools);
        body.put("tool_choice", allowTools ? "auto" : "none");
        body.put("temperature", 0.2);

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new LlmException("大模型接口超时（" + requestTimeout.toSeconds() + "s）", e);
        } catch (IOException e) {
            throw new LlmException("无法连接大模型接口：" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("大模型请求被中断", e);
        }

        if (response.statusCode() != 200) {
            throw new LlmException("大模型接口返回 HTTP " + response.statusCode() + "：" + response.body());
        }

        try {
            JsonNode message = mapper.readTree(response.body()).path("choices").path(0).path("message");
            if (!message.isObject()) {
                throw new LlmException("大模型响应中缺少 choices[0].message：" + response.body());
            }
            return (ObjectNode) message;
        } catch (IOException e) {
            throw new LlmException("无法解析大模型响应：" + response.body(), e);
        }
    }

    public static class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
