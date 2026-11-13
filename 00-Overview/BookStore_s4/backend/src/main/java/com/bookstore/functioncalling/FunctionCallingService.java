package com.bookstore.functioncalling;

import com.bookstore.functioncalling.FunctionCallingDTOs.ChatResponse;
import com.bookstore.functioncalling.FunctionCallingDTOs.ChatTurn;
import com.bookstore.functioncalling.FunctionCallingDTOs.ToolCallTrace;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 客服助手的 Function Calling 主流程：
 * 1. 携带 Tool 列表请求大模型
 * 2. 拦截模型返回的 tool_calls，打印函数名和参数
 * 3. 在本地执行对应函数，将结果以 role=tool 消息回传给模型
 * 4. 重复 1~3，直到模型不再调用函数，返回最终的自然语言回答
 */
@Service
public class FunctionCallingService {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingService.class);

    /** 最多允许模型连续调用函数的轮数，防止死循环 */
    static final int MAX_TOOL_ROUNDS = 5;

    private static final String SYSTEM_PROMPT = """
        你是“智能在线书店”的客服助手，用简洁友好的中文回答用户。
        规则：
        1. 涉及库存数量、本店价格、竞品价格时，必须调用函数获取真实数据，绝不能编造数字。
        2. 用户同时问库存和别家价格时，可以在同一轮里并行调用两个函数。
        3. 函数返回 success=false 时，阅读 error、message、hint 字段并自我纠正：
           - INVALID_ISBN / BOOK_NOT_FOUND：如果有 similar_isbns，可以用最接近的一个重试，但必须在回答中明确告诉用户“您输入的 X 没有找到，我按 Y 查询了”，并请用户确认；否则请用户核对 ISBN。
           - UPSTREAM_TIMEOUT（retryable=true）：用相同参数重试 1 次；仍失败就如实告诉用户竞品价格暂时查不到，但已查到的库存信息照常回答。
           - MISSING_ARGUMENT：向用户询问缺少的信息。
        4. 回答时给出库存数量和状态；比价时列出各平台价格，并指出哪家最便宜、与本店差价多少。金额用 ¥ 表示。
        5. 前端只显示纯文本：不要使用 **加粗**、表格、# 标题等 Markdown 语法，用换行和“- ”列表组织内容。
        """;

    private final LlmClient llmClient;
    private final BookstoreTools bookstoreTools;
    private final ObjectMapper mapper;
    private final ArrayNode tools;

    public FunctionCallingService(LlmClient llmClient, BookstoreTools bookstoreTools, ObjectMapper mapper) {
        this.llmClient = llmClient;
        this.bookstoreTools = bookstoreTools;
        this.mapper = mapper;
        this.tools = ToolDefinitions.tools(mapper);
    }

    public ArrayNode getTools() {
        return tools.deepCopy();
    }

    public ChatResponse chat(String query, List<ChatTurn> history) {
        if (query == null || query.isBlank()) {
            return new ChatResponse("请输入您的问题。", List.of(), 0, "EMPTY_QUERY");
        }

        ArrayNode messages = buildMessages(query, history);
        List<ToolCallTrace> traces = new ArrayList<>();
        ToolCallContext context = new ToolCallContext();

        try {
            for (int round = 1; round <= MAX_TOOL_ROUNDS + 1; round++) {
                // 超过最大轮数后不再提供函数，强制模型用已有信息作答
                boolean allowTools = round <= MAX_TOOL_ROUNDS;
                ObjectNode assistantMessage = llmClient.chat(messages, tools, allowTools);
                JsonNode toolCalls = assistantMessage.path("tool_calls");

                if (!allowTools || !toolCalls.isArray() || toolCalls.isEmpty()) {
                    String reply = assistantMessage.path("content").asText("").trim();
                    log.info("[Function Calling] 第 {} 轮：模型给出最终回答（共调用函数 {} 次）", round, traces.size());
                    return new ChatResponse(reply.isEmpty() ? "抱歉，我没有生成有效的回答。" : reply,
                            traces, round, null);
                }

                // 必须把带 tool_calls 的 assistant 消息原样放回上下文，后面的 tool 消息才能对上 tool_call_id
                messages.add(assistantMessage);

                for (JsonNode toolCall : toolCalls) {
                    traces.add(executeToolCall(round, toolCall, context, messages));
                }
            }
        } catch (LlmClient.LlmException e) {
            log.error("[Function Calling] 调用大模型失败：{}", e.getMessage());
            return new ChatResponse("抱歉，AI 服务暂时不可用（" + e.getMessage() + "），请稍后再试。",
                    traces, 0, "LLM_ERROR");
        }

        // for 循环最后一轮 allowTools=false，一定会 return，这里不会到达
        throw new IllegalStateException("Function Calling 循环异常结束");
    }

    /** 拦截并执行一次函数调用，把结果作为 role=tool 消息追加到上下文 */
    private ToolCallTrace executeToolCall(int round, JsonNode toolCall, ToolCallContext context, ArrayNode messages) {
        String id = toolCall.path("id").asText();
        String name = toolCall.path("function").path("name").asText();
        String rawArguments = toolCall.path("function").path("arguments").asText();

        log.info("[Function Calling] 第 {} 轮：拦截到模型调用请求 -> {}({})  tool_call_id={}",
                round, name, rawArguments, id);

        long start = System.currentTimeMillis();
        ObjectNode result = bookstoreTools.execute(name, rawArguments, context);
        long durationMs = System.currentTimeMillis() - start;
        boolean success = result.path("success").asBoolean(false);

        log.info("[Function Calling] 第 {} 轮：模拟执行 {} 完成，耗时 {}ms，结果 = {}",
                round, name, durationMs, result);

        ObjectNode toolMessage = messages.addObject();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", id);
        toolMessage.put("content", result.toString());

        return new ToolCallTrace(round, id, name, parseArguments(rawArguments), result, success, durationMs);
    }

    private ArrayNode buildMessages(String query, List<ChatTurn> history) {
        ArrayNode messages = mapper.createArrayNode();
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        if (history != null) {
            for (ChatTurn turn : history) {
                if (turn == null || turn.content() == null || turn.content().isBlank()) {
                    continue;
                }
                if ("user".equals(turn.role()) || "assistant".equals(turn.role())) {
                    messages.addObject().put("role", turn.role()).put("content", turn.content());
                }
            }
        }
        messages.addObject().put("role", "user").put("content", query);
        return messages;
    }

    private JsonNode parseArguments(String rawArguments) {
        try {
            return mapper.readTree(rawArguments);
        } catch (Exception e) {
            return TextNode.valueOf(rawArguments);
        }
    }
}
