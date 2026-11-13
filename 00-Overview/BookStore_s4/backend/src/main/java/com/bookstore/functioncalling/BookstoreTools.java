package com.bookstore.functioncalling;

import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * 两个可供大模型调用的本地函数（Skill）的实现，以及按函数名分发调用的入口。
 *
 * 所有错误都以结构化 JSON 返回给模型（error + message + hint），
 * 而不是抛异常，这样模型可以读懂错误并自我纠正（重试、换 ISBN 或向用户追问）。
 */
@Component
public class BookstoreTools {

    /** 5 位演示编号 / ISBN-10（末位可为 X）/ ISBN-13 */
    private static final Pattern ISBN_PATTERN = Pattern.compile("^(\\d{5}|\\d{9}[\\dX]|\\d{13})$");

    /** 以此前缀开头的 ISBN，在一次对话中第一次比价时模拟外部接口超时 */
    static final String SLOW_UPSTREAM_PREFIX = "999";

    private static final String[] COMPETITORS = {"京东", "当当", "淘宝"};

    private final BookRepository bookRepository;
    private final ObjectMapper mapper;
    private final long competitorTimeoutMs;
    private final long slowUpstreamDelayMs;

    @Autowired
    public BookstoreTools(BookRepository bookRepository,
                          ObjectMapper mapper,
                          @Value("${bookstore.competitor.timeout-ms:2000}") long competitorTimeoutMs,
                          @Value("${bookstore.competitor.slow-delay-ms:3000}") long slowUpstreamDelayMs) {
        this.bookRepository = bookRepository;
        this.mapper = mapper;
        this.competitorTimeoutMs = competitorTimeoutMs;
        this.slowUpstreamDelayMs = slowUpstreamDelayMs;
    }

    /**
     * 按函数名分发调用
     * @param name 模型请求调用的函数名
     * @param rawArguments 模型生成的参数（JSON 字符串，可能不合法）
     * @param context 本次对话的调用上下文（记录重试次数）
     */
    public ObjectNode execute(String name, String rawArguments, ToolCallContext context) {
        JsonNode args;
        try {
            args = mapper.readTree(rawArguments == null || rawArguments.isBlank() ? "{}" : rawArguments);
        } catch (JsonProcessingException e) {
            return error("INVALID_ARGUMENTS", "参数不是合法的 JSON：" + rawArguments,
                    "请按照函数的参数 Schema 重新生成 JSON 参数后再调用。", false);
        }

        JsonNode isbnNode = args.get("isbn");
        if (isbnNode == null || isbnNode.isNull() || isbnNode.asText().isBlank()) {
            return error("MISSING_ARGUMENT", "缺少必填参数 isbn",
                    "如果用户没有提供 ISBN，请先向用户询问 ISBN，不要编造。", false);
        }

        return switch (name) {
            case ToolDefinitions.CHECK_INVENTORY -> checkInventory(isbnNode.asText());
            case ToolDefinitions.GET_COMPETITOR_PRICE -> getCompetitorPrice(isbnNode.asText(), context);
            default -> error("UNKNOWN_FUNCTION", "不存在名为 " + name + " 的函数",
                    "只能调用 check_inventory 或 get_competitor_price。", false);
        };
    }

    /**
     * Skill 1：查询本店实时库存
     */
    public ObjectNode checkInventory(String rawIsbn) {
        String isbn = normalizeIsbn(rawIsbn);
        ObjectNode invalid = validateIsbn(rawIsbn, isbn);
        if (invalid != null) {
            return invalid;
        }

        Optional<Book> found = bookRepository.findFirstByIsbn(isbn);
        if (found.isEmpty()) {
            return bookNotFound(isbn);
        }

        Book book = found.get();
        int stock = book.getStock() == null ? 0 : book.getStock();
        ObjectNode result = mapper.createObjectNode();
        result.put("success", true);
        result.put("isbn", isbn);
        result.put("title", book.getTitle());
        result.put("author", book.getAuthor());
        result.put("our_price", book.getPrice());
        result.put("stock", stock);
        result.put("stock_status", stock == 0 ? "缺货" : stock < 10 ? "库存紧张" : "有货");
        return result;
    }

    /**
     * Skill 2：查询竞争对手价格（模拟外部 API，带超时控制）
     */
    public ObjectNode getCompetitorPrice(String rawIsbn, ToolCallContext context) {
        String isbn = normalizeIsbn(rawIsbn);
        ObjectNode invalid = validateIsbn(rawIsbn, isbn);
        if (invalid != null) {
            return invalid;
        }

        Optional<Book> found = bookRepository.findFirstByIsbn(isbn);
        if (found.isEmpty()) {
            return bookNotFound(isbn);
        }
        Book book = found.get();

        int attempt = context.nextAttempt(ToolDefinitions.GET_COMPETITOR_PRICE, isbn);
        boolean simulateSlow = isbn.startsWith(SLOW_UPSTREAM_PREFIX) && attempt == 1;

        CompletableFuture<ArrayNode> upstream = CompletableFuture.supplyAsync(() -> {
            sleep(simulateSlow ? slowUpstreamDelayMs : 150);
            return mockCompetitorQuotes(isbn, book.getPrice());
        });

        try {
            ArrayNode quotes = upstream.get(competitorTimeoutMs, TimeUnit.MILLISECONDS);
            ObjectNode result = mapper.createObjectNode();
            result.put("success", true);
            result.put("isbn", isbn);
            result.put("title", book.getTitle());
            result.put("our_price", book.getPrice());
            result.set("competitors", quotes);
            result.put("attempt", attempt);
            return result;
        } catch (TimeoutException e) {
            upstream.cancel(true);
            ObjectNode err = error("UPSTREAM_TIMEOUT",
                    "比价接口在 " + competitorTimeoutMs + "ms 内未响应（第 " + attempt + " 次调用）",
                    "这是临时故障，可以用相同参数重试 1 次；如果仍然失败，请告诉用户暂时无法获取竞品价格。", true);
            err.put("attempt", attempt);
            return err;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error("UPSTREAM_ERROR", "比价请求被中断", "请告诉用户暂时无法获取竞品价格。", true);
        } catch (ExecutionException e) {
            return error("UPSTREAM_ERROR", "比价接口异常：" + e.getCause().getMessage(),
                    "请告诉用户暂时无法获取竞品价格。", true);
        }
    }

    /** 根据 ISBN 生成确定性的模拟报价（同一本书每次查询结果一致） */
    private ArrayNode mockCompetitorQuotes(String isbn, BigDecimal ourPrice) {
        ArrayNode quotes = mapper.createArrayNode();
        for (String competitor : COMPETITORS) {
            int hash = Math.abs((isbn + competitor).hashCode());
            // 竞品价格在本店价格的 80% ~ 110% 之间浮动
            BigDecimal factor = BigDecimal.valueOf(80 + hash % 31).movePointLeft(2);
            ObjectNode quote = mapper.createObjectNode();
            quote.put("platform", competitor);
            quote.put("price", ourPrice.multiply(factor).setScale(2, RoundingMode.HALF_UP));
            quote.put("in_stock", hash % 7 != 0);
            quotes.add(quote);
        }
        return quotes;
    }

    private ObjectNode validateIsbn(String rawIsbn, String isbn) {
        if (ISBN_PATTERN.matcher(isbn).matches()) {
            return null;
        }
        return error("INVALID_ISBN",
                "ISBN \"" + rawIsbn + "\" 格式不正确：应为 5 位演示编号、10 位或 13 位数字（ISBN-10 末位可为 X）",
                "不要猜测或编造 ISBN。如果明显是笔误（如字母 O 写成了数字 0），可以修正后重试并告知用户；否则请让用户确认正确的 ISBN。",
                false);
    }

    private ObjectNode bookNotFound(String isbn) {
        List<String> similar = bookRepository.findAll().stream()
                .map(Book::getIsbn)
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .filter(candidate -> editDistance(candidate, isbn) <= 2)
                .sorted(Comparator.comparingInt(candidate -> editDistance(candidate, isbn)))
                .distinct()
                .limit(3)
                .toList();

        ObjectNode err = error("BOOK_NOT_FOUND", "本店没有 ISBN 为 " + isbn + " 的书",
                similar.isEmpty()
                        ? "请让用户确认 ISBN 是否正确。"
                        : "用户可能输错了 ISBN。可以用 similar_isbns 中最接近的一个重试，但回答时必须明确告诉用户你做了这个修正，并请用户确认。",
                false);
        ArrayNode candidates = err.putArray("similar_isbns");
        similar.forEach(candidates::add);
        return err;
    }

    private ObjectNode error(String code, String message, String hint, boolean retryable) {
        ObjectNode err = mapper.createObjectNode();
        err.put("success", false);
        err.put("error", code);
        err.put("message", message);
        err.put("hint", hint);
        err.put("retryable", retryable);
        return err;
    }

    static String normalizeIsbn(String rawIsbn) {
        return rawIsbn.replaceAll("[\\s-]", "").toUpperCase();
    }

    /** 带相邻交换的编辑距离（Optimal String Alignment），用于给出相近 ISBN 建议 */
    static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
