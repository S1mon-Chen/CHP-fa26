package com.bookstore.agent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话记忆管理器
 * 负责管理对话历史的存储、检索和清理
 */
@Service
public class ChatMemoryManager {

    private static final Logger logger = LoggerFactory.getLogger(ChatMemoryManager.class);

    /**
     * 存储对话上下文（key: conversationId）
     */
    private final Map<String, AgentContext> contextStore = new ConcurrentHashMap<>();

    /**
     * 存储用户的对话列表（key: userId, value: list of conversationIds）
     */
    private final Map<Long, List<String>> userConversations = new ConcurrentHashMap<>();

    /**
     * 最大上下文保留时间（30分钟）
     */
    private static final long MAX_CONTEXT_AGE_MINUTES = 30;

    /**
     * 最大对话历史记录数
     */
    private static final int MAX_HISTORY_SIZE = 100;

    /**
     * 获取或创建对话上下文
     * @param conversationId 对话ID
     * @return AgentContext 实例
     */
    public AgentContext getOrCreateContext(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = generateConversationId();
        }

        AgentContext context = contextStore.get(conversationId);
        
        if (context == null) {
            context = AgentContext.builder()
                    .conversationId(conversationId)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .preferences(new HashMap<>())
                    .executionHistory(new LinkedList<>())
                    .data(new HashMap<>())
                    .build();
            contextStore.put(conversationId, context);
            logger.info("创建新对话上下文: {}", conversationId);
        } else {
            // 更新最后访问时间
            context.setUpdatedAt(LocalDateTime.now());
        }

        return context;
    }

    /**
     * 根据用户ID获取对话上下文
     * 如果用户有多个对话，返回最近活跃的那个
     * @param userId 用户ID
     * @return AgentContext 实例，如果没有找到返回 null
     */
    public AgentContext getContextByUserId(Long userId) {
        if (userId == null) {
            return null;
        }

        List<String> conversationIds = userConversations.get(userId);
        if (conversationIds == null || conversationIds.isEmpty()) {
            return null;
        }

        // 查找最近活跃的对话
        AgentContext latestContext = null;
        LocalDateTime latestTime = null;

        for (String conversationId : conversationIds) {
            AgentContext context = contextStore.get(conversationId);
            if (context != null) {
                LocalDateTime updatedAt = context.getUpdatedAt();
                if (latestTime == null || updatedAt.isAfter(latestTime)) {
                    latestTime = updatedAt;
                    latestContext = context;
                }
            }
        }

        return latestContext;
    }

    /**
     * 保存对话上下文
     * @param context 对话上下文
     */
    public void saveContext(AgentContext context) {
        if (context == null || context.getConversationId() == null) {
            return;
        }

        context.setUpdatedAt(LocalDateTime.now());
        contextStore.put(context.getConversationId(), context);

        // 更新用户对话列表
        if (context.getUserId() != null) {
            userConversations.computeIfAbsent(context.getUserId(), k -> new LinkedList<>())
                    .remove(context.getConversationId());
            userConversations.get(context.getUserId()).add(0, context.getConversationId());

            // 限制每个用户的对话数量
            List<String> conversations = userConversations.get(context.getUserId());
            if (conversations.size() > MAX_HISTORY_SIZE) {
                String removedId = conversations.remove(conversations.size() - 1);
                contextStore.remove(removedId);
                logger.debug("移除旧对话: {}", removedId);
            }
        }

        logger.debug("保存对话上下文: {}", context.getConversationId());
    }

    /**
     * 删除对话上下文
     * @param conversationId 对话ID
     */
    public void deleteContext(String conversationId) {
        AgentContext context = contextStore.remove(conversationId);
        if (context != null && context.getUserId() != null) {
            List<String> conversations = userConversations.get(context.getUserId());
            if (conversations != null) {
                conversations.remove(conversationId);
            }
        }
        logger.info("删除对话上下文: {}", conversationId);
    }

    /**
     * 删除用户的所有对话上下文
     * @param userId 用户ID
     */
    public void deleteUserContexts(Long userId) {
        List<String> conversationIds = userConversations.remove(userId);
        if (conversationIds != null) {
            for (String conversationId : conversationIds) {
                contextStore.remove(conversationId);
            }
            logger.info("删除用户所有对话上下文: userId={}, 数量={}", userId, conversationIds.size());
        }
    }

    /**
     * 获取用户的对话列表
     * @param userId 用户ID
     * @return 对话上下文列表
     */
    public List<AgentContext> getUserContexts(Long userId) {
        List<AgentContext> contexts = new ArrayList<>();
        List<String> conversationIds = userConversations.get(userId);
        
        if (conversationIds != null) {
            for (String conversationId : conversationIds) {
                AgentContext context = contextStore.get(conversationId);
                if (context != null) {
                    contexts.add(context);
                }
            }
        }
        
        return contexts;
    }

    /**
     * 清理过期的上下文
     * @return 清理的上下文数量
     */
    public int cleanupExpiredContexts() {
        int cleanedCount = 0;
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(MAX_CONTEXT_AGE_MINUTES);

        Iterator<Map.Entry<String, AgentContext>> iterator = contextStore.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AgentContext> entry = iterator.next();
            AgentContext context = entry.getValue();
            
            if (context.getUpdatedAt() != null && context.getUpdatedAt().isBefore(threshold)) {
                iterator.remove();
                
                // 同时从用户对话列表中移除
                if (context.getUserId() != null) {
                    List<String> conversations = userConversations.get(context.getUserId());
                    if (conversations != null) {
                        conversations.remove(entry.getKey());
                    }
                }
                
                cleanedCount++;
                logger.debug("清理过期上下文: {}", entry.getKey());
            }
        }

        if (cleanedCount > 0) {
            logger.info("清理过期上下文完成，共清理 {} 个", cleanedCount);
        }

        return cleanedCount;
    }

    /**
     * 获取上下文数量
     * @return 上下文数量
     */
    public int getContextCount() {
        return contextStore.size();
    }

    /**
     * 生成唯一对话ID
     * @return 对话ID
     */
    public String generateConversationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 添加对话记录
     * @param conversationId 对话ID
     * @param agentName 智能体名称
     * @param input 用户输入
     * @param result 响应结果
     * @param success 是否成功
     */
    public void addExecutionRecord(String conversationId, String agentName, 
                                   String input, String result, boolean success) {
        AgentContext context = getOrCreateContext(conversationId);
        context.addExecutionRecord(agentName, input, result, success);
        saveContext(context);
    }

    /**
     * 获取对话历史记录
     * @param conversationId 对话ID
     * @return 执行记录列表
     */
    public List<AgentContext.ExecutionRecord> getExecutionHistory(String conversationId) {
        AgentContext context = contextStore.get(conversationId);
        return context != null ? context.getExecutionHistory() : Collections.emptyList();
    }
}