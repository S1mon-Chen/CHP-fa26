package com.bookstore.functioncalling;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Function Calling 对话接口的请求/响应数据结构
 */
public final class FunctionCallingDTOs {

    private FunctionCallingDTOs() {
    }

    /** 历史消息，role 为 user 或 assistant */
    public record ChatTurn(String role, String content) {
    }

    public record ChatRequest(String query, List<ChatTurn> history) {
    }

    /** 一次函数调用的完整记录，返回给前端展示 */
    public record ToolCallTrace(int round, String id, String name, JsonNode arguments,
                                JsonNode result, boolean success, long durationMs) {
    }

    public record ChatResponse(String reply, List<ToolCallTrace> toolCalls, int rounds, String error) {
    }
}
