package com.bookstore.agent.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存上下文存储仓库实现
 * 使用 ConcurrentHashMap 存储上下文，适合开发和测试环境
 */
@Repository
public class InMemoryContextRepository implements ContextRepository {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryContextRepository.class);

    /**
     * 存储上下文的Map（key: conversationId）
     */
    private final ConcurrentHashMap<String, AgentContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 用户ID到对话ID的映射（key: userId, value: list of conversationIds）
     */
    private final ConcurrentHashMap<Long, List<String>> userConversations = new ConcurrentHashMap<>();

    @Override
    public AgentContext save(AgentContext context) {
        if (context == null || context.getConversationId() == null) {
            throw new IllegalArgumentException("上下文或对话ID不能为空");
        }

        // 更新时间戳
        context.setUpdatedAt(java.time.LocalDateTime.now());

        // 保存上下文
        contextMap.put(context.getConversationId(), context);

        // 更新用户对话列表
        if (context.getUserId() != null) {
            userConversations.computeIfAbsent(context.getUserId(), k -> new ArrayList<>())
                    .remove(context.getConversationId());
            userConversations.get(context.getUserId()).add(0, context.getConversationId());
        }

        logger.debug("保存上下文: conversationId={}, userId={}", 
                    context.getConversationId(), context.getUserId());

        return context;
    }

    @Override
    public Optional<AgentContext> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return Optional.empty();
        }

        AgentContext context = contextMap.get(conversationId);
        return Optional.ofNullable(context);
    }

    @Override
    public List<AgentContext> findByUserId(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        List<String> conversationIds = userConversations.get(userId);
        if (conversationIds == null || conversationIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<AgentContext> contexts = new ArrayList<>();
        for (String conversationId : conversationIds) {
            AgentContext context = contextMap.get(conversationId);
            if (context != null) {
                contexts.add(context);
            }
        }

        return contexts;
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return;
        }

        AgentContext context = contextMap.remove(conversationId);
        if (context != null && context.getUserId() != null) {
            List<String> conversationIds = userConversations.get(context.getUserId());
            if (conversationIds != null) {
                conversationIds.remove(conversationId);
            }
        }

        logger.debug("删除上下文: {}", conversationId);
    }

    @Override
    public void deleteByUserId(Long userId) {
        if (userId == null) {
            return;
        }

        List<String> conversationIds = userConversations.remove(userId);
        if (conversationIds != null) {
            for (String conversationId : conversationIds) {
                contextMap.remove(conversationId);
            }
        }

        logger.debug("删除用户所有上下文: userId={}", userId);
    }

    @Override
    public void deleteAll() {
        int count = contextMap.size();
        contextMap.clear();
        userConversations.clear();
        logger.info("删除所有上下文，共 {} 个", count);
    }

    @Override
    public long count() {
        return contextMap.size();
    }

    @Override
    public boolean existsByConversationId(String conversationId) {
        return conversationId != null && contextMap.containsKey(conversationId);
    }
}