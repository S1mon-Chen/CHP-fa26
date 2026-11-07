package com.bookstore.assistant;

import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Component
public class BookTools {
    private final BookRepository books;
    private final ObjectMapper mapper;
    private final AssistantSettings settings;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public BookTools(BookRepository books, ObjectMapper mapper, AssistantSettings settings) {
        this.books = books;
        this.mapper = mapper;
        this.settings = settings;
    }

    public ObjectNode execute(String name, String arguments) {
        // Explicit allowlist: never use reflection, eval, shell commands or model-supplied URLs.
        if (!name.equals("check_inventory") && !name.equals("get_competitor_price")) {
            return error("UNKNOWN_TOOL", "只允许 check_inventory 和 get_competitor_price。", false);
        }
        try {
            JsonNode args = mapper.readTree(arguments);
            if (args == null || !args.isObject() || args.size() != 1 || !args.path("isbn").isTextual()) {
                return error("INVALID_ARGUMENTS", "参数必须是只含字符串 isbn 的 JSON 对象。", false);
            }
            String isbn = args.get("isbn").asText();
            ObjectNode invalid = validateIsbn(isbn);
            if (invalid != null) return invalid;
            return name.equals("check_inventory") ? check_inventory(isbn) : get_competitor_price(isbn);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return error("INVALID_ARGUMENTS", "arguments 不是合法 JSON，请重新生成参数。", false);
        } catch (RuntimeException e) {
            return error("TOOL_FAILED", "查询服务暂时不可用，请说明情况，不要编造数据。", false);
        }
    }

    public ObjectNode check_inventory(String isbn) {
        ObjectNode invalid = validateIsbn(isbn);
        if (invalid != null) return invalid;
        return books.findFirstByIsbnOrderByIdAsc(isbn).map(book -> {
            ObjectNode result = mapper.createObjectNode().put("ok", true).put("isbn", isbn)
                    .put("title", book.getTitle()).put("stock", book.getStock())
                    .put("price", book.getPrice()).put("currency", "CNY")
                    .put("source", "bookstore_database").put("checked_at", Instant.now().toString());
            return result;
        }).orElseGet(() -> error("BOOK_NOT_FOUND", "本店没有此 ISBN 的记录，请向用户确认书号。", false));
    }

    public ObjectNode get_competitor_price(String isbn) {
        ObjectNode invalid = validateIsbn(isbn);
        if (invalid != null) return invalid;
        try {
            URI uri = URI.create(settings.getCompetitorUrl() + "?isbn=" + URLEncoder.encode(isbn, StandardCharsets.UTF_8));
            var request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(settings.getCompetitorTimeoutMs())).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return error("PRICE_NOT_FOUND", "模拟竞品没有此 ISBN 的报价。", false);
            if (response.statusCode() != 200) return error("COMPETITOR_UNAVAILABLE", "模拟竞品接口暂时不可用。", true);
            JsonNode price = mapper.readTree(response.body());
            if (!price.path("price").isNumber() || price.path("price").decimalValue().signum() < 0
                    || !isbn.equals(price.path("isbn").asText()) || !"CNY".equals(price.path("currency").asText())
                    || !price.path("competitor").isTextual()) {
                return error("INVALID_COMPETITOR_RESPONSE", "模拟竞品返回了无效的价格数据。", false);
            }
            // Only copy known fields; do not pass arbitrary external instructions to the model.
            return mapper.createObjectNode().put("ok", true).put("isbn", isbn)
                    .put("price", price.path("price").decimalValue()).put("currency", "CNY")
                    .put("competitor", price.path("competitor").asText()).put("simulated", true)
                    .put("checked_at", Instant.now().toString());
        } catch (HttpTimeoutException e) {
            return error("COMPETITOR_TIMEOUT", "模拟竞品接口超时，可以重试一次；仍失败则说明暂无报价。", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("INTERRUPTED", "查询被中断。", false);
        } catch (IOException e) {
            return error("COMPETITOR_UNAVAILABLE", "模拟竞品接口无法连接或响应不是合法 JSON。", true);
        }
    }

    private ObjectNode validateIsbn(String isbn) {
        if (isbn != null && validIsbn(isbn)) return null;
        ObjectNode result = error("INVALID_ISBN", "ISBN 格式或校验位不正确。请使用 ISBN-10、ISBN-13 或教学样例 12345，不要猜测缺失数字。", false);
        if (isbn != null && isbn.length() <= 40) {
            String normalized = isbn.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
            if (validIsbn(normalized)) {
                ((ObjectNode) result.get("error")).put("suggested_isbn", normalized)
                        .put("message", "请去除空格和连字符并将末尾 x 大写，再用 suggested_isbn 重新调用工具。");
            }
        }
        return result;
    }

    private boolean validIsbn(String isbn) {
        if ("12345".equals(isbn)) return true;
        if (isbn.matches("97[89][0-9]{10}")) {
            int sum = 0;
            for (int i = 0; i < 13; i++) sum += (isbn.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
            return sum % 10 == 0;
        }
        if (isbn.matches("[0-9]{9}[0-9X]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) sum += (isbn.charAt(i) == 'X' ? 10 : isbn.charAt(i) - '0') * (10 - i);
            return sum % 11 == 0;
        }
        return false;
    }

    public ObjectNode error(String code, String message, boolean retryable) {
        ObjectNode result = mapper.createObjectNode().put("ok", false);
        result.putObject("error").put("code", code).put("message", message).put("retryable", retryable);
        return result;
    }
}
