package com.bookstore.agent.context;

import java.util.List;
import java.util.Optional;

/**
 * 上下文存储仓库接口
 * 定义上下文的持久化操作
 */
public interface ContextRepository {

    /**
     * 保存上下文
     * @param context 上下文对象
     * @return 保存后的上下文
     */
    AgentContext save(AgentContext context);

    /**
     * 根据对话ID查找上下文
     * @param conversationId 对话ID
     * @return 上下文对象，如果没有找到返回 Optional.empty()
     */
    Optional<AgentContext> findByConversationId(String conversationId);

    /**
     * 根据用户ID查找上下文列表
     * @param userId 用户ID
     * @return 上下文列表
     */
    List<AgentContext> findByUserId(Long userId);

    /**
     * 根据对话ID删除上下文
     * @param conversationId 对话ID
     */
    void deleteByConversationId(String conversationId);

    /**
     * 根据用户ID删除所有上下文
     * @param userId 用户ID
     */
    void deleteByUserId(Long userId);

    /**
     * 删除所有上下文
     */
    void deleteAll();

    /**
     * 统计上下文数量
     * @return 上下文数量
     */
    long count();

    /**
     * 检查上下文是否存在
     * @param conversationId 对话ID
     * @return 如果存在返回 true，否则返回 false
     */
    boolean existsByConversationId(String conversationId);
}