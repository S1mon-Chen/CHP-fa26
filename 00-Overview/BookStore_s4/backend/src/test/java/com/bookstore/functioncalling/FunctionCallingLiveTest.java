package com.bookstore.functioncalling;

import com.bookstore.functioncalling.FunctionCallingDTOs.ChatResponse;
import com.bookstore.functioncalling.FunctionCallingDTOs.ToolCallTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端测试：真实调用 DeepSeek，数据库用 Mock 代替。
 * 只有设置了 LLM_API_KEY 环境变量时才运行。
 */
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
class FunctionCallingLiveTest {

    private FunctionCallingService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        LlmClient llmClient = new LlmClient(mapper, "https://api.deepseek.com", "deepseek-flash",
                System.getenv("LLM_API_KEY"), 90);
        BookstoreTools tools = new BookstoreTools(BookstoreToolsTest.mockRepository(), mapper, 500, 1500);
        service = new FunctionCallingService(llmClient, tools, mapper);
    }

    @Test
    void assignmentExampleCallsBothFunctions() {
        ChatResponse response = service.chat("帮我查一下 ISBN为12345 的书还有多少本，顺便看看别家卖多少钱", List.of());
        print(response);

        assertNull(response.error());
        assertTrue(response.toolCalls().stream().anyMatch(t -> t.name().equals("check_inventory")
                && t.arguments().path("isbn").asText().equals("12345") && t.success()));
        assertTrue(response.toolCalls().stream().anyMatch(t -> t.name().equals("get_competitor_price") && t.success()));
        assertTrue(response.reply().contains("100"), "回答中应包含库存数量 100");
    }

    @Test
    void mistypedIsbnIsSelfCorrected() {
        ChatResponse response = service.chat("ISBN 12354 这本书还有货吗？", List.of());
        print(response);

        assertTrue(response.toolCalls().stream().anyMatch(t -> "BOOK_NOT_FOUND".equals(t.result().path("error").asText())));
        assertTrue(response.toolCalls().stream().anyMatch(t -> t.success()
                && t.arguments().path("isbn").asText().equals("12345")), "模型应根据 similar_isbns 改用 12345 重试");
        assertTrue(response.reply().contains("12345"));
    }

    @Test
    void upstreamTimeoutIsRetried() {
        ChatResponse response = service.chat("ISBN 99901 这本书别家卖多少钱？", List.of());
        print(response);

        List<ToolCallTrace> priceCalls = response.toolCalls().stream()
                .filter(t -> t.name().equals("get_competitor_price")).toList();
        assertEquals("UPSTREAM_TIMEOUT", priceCalls.get(0).result().path("error").asText());
        assertTrue(priceCalls.size() >= 2 && priceCalls.get(1).success(), "超时后应重试并成功");
    }

    private static void print(ChatResponse response) {
        System.out.println("==== 函数调用记录 ====");
        response.toolCalls().forEach(t -> System.out.printf("第%d轮 %s(%s) -> %s%n",
                t.round(), t.name(), t.arguments(), t.result()));
        System.out.println("==== 最终回答（共 " + response.rounds() + " 轮）====\n" + response.reply());
    }
}
