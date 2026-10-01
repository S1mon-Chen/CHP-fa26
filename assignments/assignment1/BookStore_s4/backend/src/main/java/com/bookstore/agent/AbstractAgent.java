package com.bookstore.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能体抽象基类
 * 提供智能体的通用功能实现
 */
public abstract class AbstractAgent implements Agent {

    /**
     * 日志记录器
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 默认实现：返回类名作为智能体名称
     * @return 智能体名称（类名）
     */
    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 执行智能体逻辑（模板方法）
     * @param input 用户输入
     * @param context 上下文信息
     * @return 智能体执行结果
     */
    @Override
    public AgentResponse execute(String input, Map<String, Object> context) {
        // 记录开始执行日志
        logInfo("开始执行智能体: {}, 用户输入: {}", getName(), input);

        try {
            // 验证上下文
            validateContext(context);

            // 调用子类实现的具体逻辑
            AgentResponse response = doExecute(input, context);

            // 记录执行结果日志
            if (response.isSuccess()) {
                logInfo("智能体 {} 执行成功", getName());
            } else {
                logWarn("智能体 {} 执行失败: {}", getName(), response.getResult());
            }

            return response;

        } catch (Exception e) {
            // 记录异常日志
            logError("智能体 {} 执行异常: {}", getName(), e.getMessage(), e);
            return AgentResponse.failure("智能体执行异常: " + e.getMessage(), context);
        }
    }

    /**
     * 子类必须实现的具体执行逻辑
     * @param input 用户输入
     * @param context 上下文信息
     * @return 智能体执行结果
     */
    protected abstract AgentResponse doExecute(String input, Map<String, Object> context);

    /**
     * 验证上下文信息
     * 如果上下文为 null，则创建一个新的空上下文
     * @param context 上下文信息
     * @return 验证后的上下文
     */
    protected Map<String, Object> validateContext(Map<String, Object> context) {
        if (context == null) {
            logWarn("上下文为 null，将创建新的空上下文");
            return new HashMap<>();
        }
        return context;
    }

    /**
     * 获取上下文中的用户ID
     * @param context 上下文信息
     * @return 用户ID，如果不存在返回 null
     */
    protected Long getUserId(Map<String, Object> context) {
        Object userIdObj = context != null ? context.get("userId") : null;
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        } else if (userIdObj instanceof String) {
            try {
                return Long.parseLong((String) userIdObj);
            } catch (NumberFormatException e) {
                logWarn("无法解析用户ID: {}", userIdObj);
                return null;
            }
        }
        return null;
    }

    /**
     * 判断用户是否已登录
     * @param context 上下文信息
     * @return 如果已登录返回 true，否则返回 false
     */
    protected boolean isUserLoggedIn(Map<String, Object> context) {
        return getUserId(context) != null;
    }

    /**
     * 记录信息日志
     * @param message 日志消息
     * @param args 消息参数
     */
    protected void logInfo(String message, Object... args) {
        logger.info(message, args);
    }

    /**
     * 记录警告日志
     * @param message 日志消息
     * @param args 消息参数
     */
    protected void logWarn(String message, Object... args) {
        logger.warn(message, args);
    }

    /**
     * 记录错误日志
     * @param message 日志消息
     * @param args 消息参数
     */
    protected void logError(String message, Object... args) {
        logger.error(message, args);
    }
}