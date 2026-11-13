package com.bookstore.controller;

import com.bookstore.agent.CoordinatorAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI助手控制器
 * 处理AI助手相关的HTTP请求
 */
@RestController
@RequestMapping("/api/ai")
public class AIAssistantController {

    @Autowired
    private CoordinatorAgent coordinatorAgent;

    /**
     * 处理用户聊天请求（使用多智能体系统）
     * @param request 聊天请求
     * @return AI响应
     */
    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest request) {
        return coordinatorAgent.handleRequest(request.getQuery(), request.getUserId());
    }

    /**
     * 健康检查
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("agentCount", coordinatorAgent.getAgentCount());
        result.put("agents", coordinatorAgent.getRegisteredAgents());
        return result;
    }

    /**
     * 获取已注册的智能体列表
     * @return 智能体列表
     */
    @GetMapping("/agents")
    public Map<String, Object> getAgents() {
        Map<String, Object> result = new HashMap<>();
        result.put("agents", coordinatorAgent.getRegisteredAgents());
        result.put("count", coordinatorAgent.getAgentCount());
        return result;
    }

    /**
     * 聊天请求DTO
     */
    public static class ChatRequest {
        private String query;
        private Long userId;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }
}