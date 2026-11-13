package com.bookstore.functioncalling;

import java.util.HashMap;
import java.util.Map;

/**
 * 一次对话请求内的函数调用上下文，记录每个 (函数, ISBN) 被调用的次数，
 * 用于模拟“首次调用超时、重试成功”的场景。
 */
public class ToolCallContext {

    private final Map<String, Integer> attempts = new HashMap<>();

    public synchronized int nextAttempt(String functionName, String isbn) {
        return attempts.merge(functionName + ":" + isbn, 1, Integer::sum);
    }
}
