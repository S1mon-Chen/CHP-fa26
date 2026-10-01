package com.bookstore.agent;

import java.util.Map;

/**
 * 智能体接口定义
 * 所有智能体都必须实现此接口
 */
public interface Agent {

    /**
     * 获取智能体名称
     * @return 智能体名称
     */
    String getName();

    /**
     * 判断智能体是否能处理指定意图
     * @param intent 用户意图字符串
     * @return 如果能处理返回 true，否则返回 false
     */
    boolean canHandle(String intent);

    /**
     * 执行智能体逻辑
     * @param input 用户输入
     * @param context 上下文信息，包含用户信息、对话历史等
     * @return 智能体执行结果
     */
    AgentResponse execute(String input, Map<String, Object> context);
}