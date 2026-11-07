package com.bookstore.agent;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能体执行结果封装类
 */
public class AgentResponse {

    private boolean success;
    private String result;
    private Map<String, Object> context = new HashMap<>();
    private String nextAgent;

    public AgentResponse() {
        this.context = new HashMap<>();
    }

    public AgentResponse(boolean success, String result, Map<String, Object> context, String nextAgent) {
        this.success = success;
        this.result = result;
        this.context = context != null ? context : new HashMap<>();
        this.nextAgent = nextAgent;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context != null ? context : new HashMap<>(); }
    public String getNextAgent() { return nextAgent; }
    public void setNextAgent(String nextAgent) { this.nextAgent = nextAgent; }

    public static AgentResponse success(String result) {
        return new AgentResponse(true, result, new HashMap<>(), null);
    }

    public static AgentResponse success(String result, Map<String, Object> context) {
        return new AgentResponse(true, result, context, null);
    }

    public static AgentResponse success(String result, Map<String, Object> context, String nextAgent) {
        return new AgentResponse(true, result, context, nextAgent);
    }

    public static AgentResponse failure(String errorMessage) {
        return new AgentResponse(false, errorMessage, new HashMap<>(), null);
    }

    public static AgentResponse failure(String errorMessage, Map<String, Object> context) {
        return new AgentResponse(false, errorMessage, context, null);
    }
}