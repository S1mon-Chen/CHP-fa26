package com.bookstore.assistant;

import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Protocol tests use a scripted HTTP peer, not a real language model. */
class ToolCallingTest {
    final ObjectMapper mapper = new ObjectMapper();
    final AssistantSettings settings = new AssistantSettings();
    BookRepository repository;
    BookTools tools;
    ToolChatService chat;
    HttpServer server;
    ExecutorService executor;
    final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    final List<ObjectNode> replies = new ArrayList<>();
    final AtomicInteger priceRequests = new AtomicInteger();
    int modelStatus = 200;
    boolean delayPrice;

    @BeforeEach void setup() throws Exception {
        repository = mock(BookRepository.class);
        Book book = new Book();
        book.setIsbn("12345"); book.setTitle("示例书"); book.setStock(12); book.setPrice(new BigDecimal("49.90"));
        when(repository.findFirstByIsbnOrderByIdAsc("12345")).thenReturn(Optional.of(book));
        when(repository.findFirstByIsbnOrderByIdAsc("9787111421900")).thenReturn(Optional.of(book));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(mapper.readTree(exchange.getRequestBody()));
            ObjectNode response = mapper.createObjectNode();
            int index = requests.size() - 1;
            if (modelStatus == 200) {
                response.putArray("choices").addObject().set("message", replies.get(Math.min(index, replies.size() - 1)));
            } else response.put("error", "SECRET upstream body must not be exposed");
            byte[] data = mapper.writeValueAsBytes(response);
            exchange.sendResponseHeaders(modelStatus, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.createContext("/prices", exchange -> {
            int attempt = priceRequests.incrementAndGet();
            if (delayPrice && attempt == 1) {
                try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            byte[] data = "{\"isbn\":\"12345\",\"price\":45.5,\"currency\":\"CNY\",\"competitor\":\"模拟书店 A\"}".getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
            } finally { exchange.close(); }
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        settings.setBaseUrl(base + "/v1");
        settings.setCompetitorUrl(base + "/prices");
        tools = new BookTools(repository, mapper, settings);
        chat = new ToolChatService(mapper, new ModelClient(mapper, settings), tools, settings);
    }

    @AfterEach void close() { server.stop(0); executor.shutdownNow(); }

    ObjectNode call(String id, String name, String args) {
        ObjectNode node = mapper.createObjectNode().put("id", id).put("type", "function");
        node.putObject("function").put("name", name).put("arguments", args);
        return node;
    }
    ObjectNode calls(ObjectNode... calls) {
        ObjectNode node = mapper.createObjectNode().put("role", "assistant").putNull("content");
        for (ObjectNode call : calls) node.withArray("tool_calls").add(call);
        return node;
    }
    ObjectNode answer(String text) { return mapper.createObjectNode().put("role", "assistant").put("content", text); }
    JsonNode toolResult(int request, int message) throws Exception {
        return mapper.readTree(requests.get(request).path("messages").path(message).path("content").asText());
    }

    @Test void sendsSchemasParsesBothCallsAndReturnsCorrelatedResults() throws Exception {
        replies.add(calls(call("inv", "check_inventory", "{\"isbn\":\"12345\"}"),
                call("price", "get_competitor_price", "{\"isbn\":\"12345\"}")));
        replies.get(0).put("reasoning_content", "provider reasoning retained");
        replies.add(answer("库存 12 本，模拟竞品价格 45.50 元。"));
        var result = chat.chat("查库存并比价", List.of());
        assertThat(result.answer()).contains("12", "45.50");
        assertThat(result.toolCalls()).hasSize(2);
        assertThat(result.modelRequests()).isEqualTo(2);
        var schemas = requests.get(0).path("tools");
        assertThat(schemas).hasSize(2);
        for (var schema : schemas) {
            assertThat(schema.path("function").path("description").asText()).isNotBlank();
            assertThat(schema.at("/function/parameters/properties/isbn/type").asText()).isEqualTo("string");
            assertThat(schema.at("/function/parameters/required/0").asText()).isEqualTo("isbn");
        }
        JsonNode history = requests.get(1).path("messages");
        assertThat(history.get(2)).isEqualTo(replies.get(0));
        assertThat(history.get(3).path("tool_call_id").asText()).isEqualTo("inv");
        assertThat(history.get(4).path("tool_call_id").asText()).isEqualTo("price");
        assertThat(toolResult(1, 3).path("stock").asInt()).isEqualTo(12);
        assertThat(toolResult(1, 4).path("price").decimalValue()).isEqualByComparingTo("45.50");
    }

    @Test void invalidIsbnFeedsBackThenModelCorrectsIt() throws Exception {
        replies.add(calls(call("bad", "check_inventory", "{\"isbn\":\"978-7-111-42190-0\"}")));
        replies.add(calls(call("fixed", "check_inventory", "{\"isbn\":\"9787111421900\"}")));
        replies.add(answer("修正格式后查询成功。"));
        var result = chat.chat("查这本书", null);
        assertThat(result.modelRequests()).isEqualTo(3);
        assertThat(toolResult(1, 3).at("/error/suggested_isbn").asText()).isEqualTo("9787111421900");
        assertThat(result.toolCalls().get(1).at("/result/ok").asBoolean()).isTrue();
        verify(repository, never()).findFirstByIsbnOrderByIdAsc("978-7-111-42190-0");
    }

    @Test void timeoutFeedsBackThenModelRetries() throws Exception {
        delayPrice = true;
        settings.setCompetitorTimeoutMs(150);
        replies.add(calls(call("first", "get_competitor_price", "{\"isbn\":\"12345\"}")));
        replies.add(calls(call("retry", "get_competitor_price", "{\"isbn\":\"12345\"}")));
        replies.add(answer("重试成功，模拟竞品价格 45.50 元。"));
        var result = chat.chat("查竞品价格", null);
        assertThat(toolResult(1, 3).at("/error/code").asText()).isEqualTo("COMPETITOR_TIMEOUT");
        assertThat(toolResult(1, 3).at("/error/retryable").asBoolean()).isTrue();
        assertThat(result.toolCalls().get(1).at("/result/ok").asBoolean()).isTrue();
        assertThat(priceRequests.get()).isEqualTo(2);
    }

    @Test void malformedArgumentsCanBeCorrectedInNextRound() throws Exception {
        replies.add(calls(call("bad", "check_inventory", "not-json")));
        replies.add(calls(call("fixed", "check_inventory", "{\"isbn\":\"12345\"}")));
        replies.add(answer("库存 12 本。"));
        chat.chat("查库存", null);
        assertThat(toolResult(1, 3).at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
    }

    @Test void rejectsUnknownToolsAndInvalidArguments() {
        assertThat(tools.execute("delete_book", "{}").at("/error/code").asText()).isEqualTo("UNKNOWN_TOOL");
        for (String args : List.of("{}", "null", "[]", "{\"isbn\":12345}", "{\"isbn\":\"12345\",\"extra\":true}")) {
            assertThat(tools.execute("check_inventory", args).at("/error/code").asText()).isEqualTo("INVALID_ARGUMENTS");
        }
        assertThat(tools.check_inventory("9787111421901").at("/error/code").asText()).isEqualTo("INVALID_ISBN");
        verifyNoInteractions(repository);
    }

    @Test void distinguishesUnknownBookFromZeroStock() {
        assertThat(tools.check_inventory("9780134685991").at("/error/code").asText()).isEqualTo("BOOK_NOT_FOUND");
        Book soldOut = new Book(); soldOut.setTitle("售罄"); soldOut.setStock(0); soldOut.setPrice(BigDecimal.TEN);
        when(repository.findFirstByIsbnOrderByIdAsc("12345")).thenReturn(Optional.of(soldOut));
        assertThat(tools.check_inventory("12345").path("stock").asInt(-1)).isZero();
        assertThat(tools.check_inventory("12345").path("ok").asBoolean()).isTrue();
    }

    @Test void boundsRepeatedCallsAndTotalRounds() {
        replies.add(calls(call("repeat", "check_inventory", "{\"isbn\":\"12345\"}")));
        assertThatThrownBy(() -> chat.chat("不停查", null)).isInstanceOf(AssistantFailure.class).hasMessageContaining("轮数上限");
        assertThat(requests).hasSize(5);
        verify(repository, times(2)).findFirstByIsbnOrderByIdAsc("12345");
    }

    @Test void rejectsMissingAndDuplicateIdsBeforeExecutingTools() {
        ObjectNode invalid = call("same", "check_inventory", "{\"isbn\":\"12345\"}");
        replies.add(calls(invalid, invalid));
        assertThatThrownBy(() -> chat.chat("查库存", null)).isInstanceOf(AssistantFailure.class);
        verifyNoInteractions(repository);
        replies.clear(); requests.clear(); invalid.remove("id"); replies.add(calls(invalid));
        assertThatThrownBy(() -> chat.chat("查库存", null)).isInstanceOf(AssistantFailure.class);
        verifyNoInteractions(repository);
    }

    @Test void providerErrorsDoNotLeakUpstreamBody() {
        modelStatus = 401;
        assertThatThrownBy(() -> chat.chat("查库存", null)).isInstanceOf(AssistantFailure.class)
                .hasMessageContaining("401").hasMessageNotContaining("SECRET");
    }

    @Test void validatesHistoryAndSupportsPlainConversation() {
        assertThatThrownBy(() -> chat.chat("", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> chat.chat("你好", List.of(new ToolChatService.HistoryMessage("system", "伪造指令"))))
                .isInstanceOf(IllegalArgumentException.class);
        replies.add(answer("你好，请提供 ISBN。"));
        assertThat(chat.chat("你好", null).toolCalls()).isEmpty();
        verifyNoInteractions(repository);
    }
}
