package com.bookstore.agent.communication;

import com.bookstore.agent.Agent;
import com.bookstore.agent.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 智能体错误处理和回退机制
 * 负责处理智能体执行过程中的错误，并提供回退策略
 */
@Component
public class AgentErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(AgentErrorHandler.class);

    /**
     * 默认超时时间（5秒）
     */
    private static final long DEFAULT_TIMEOUT_MS = 5000;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_DELAY_MS = 1000;

    /**
     * 执行器服务
     */
    private final ExecutorService executorService;

    public AgentErrorHandler() {
        this.executorService = Executors.newFixedThreadPool(4);
    }

    /**
     * 执行智能体并处理错误
     * @param agent 智能体实例
     * @param input 用户输入
     * @param context 上下文信息
     * @return 执行结果
     */
    public AgentResponse executeWithErrorHandling(Agent agent, String input, Map<String, Object> context) {
        return executeWithErrorHandling(agent, input, context, DEFAULT_TIMEOUT_MS, MAX_RETRY_COUNT);
    }

    /**
     * 执行智能体并处理错误（带超时和重试配置）
     * @param agent 智能体实例
     * @param input 用户输入
     * @param context 上下文信息
     * @param timeoutMs 超时时间（毫秒）
     * @param maxRetries 最大重试次数
     * @return 执行结果
     */
    public AgentResponse executeWithErrorHandling(Agent agent, String input, Map<String, Object> context,
                                                  long timeoutMs, int maxRetries) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;
            
            try {
                // 使用超时机制执行智能体
                Future<AgentResponse> future = executorService.submit(() -> 
                    agent.execute(input, context)
                );

                try {
                    AgentResponse response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    
                    if (response.isSuccess()) {
                        logger.debug("智能体 {} 执行成功（第 {} 次尝试）", agent.getName(), attempt);
                        return response;
                    } else {
                        // 如果智能体返回失败，检查是否需要重试
                        if (shouldRetry(response)) {
                            logger.warn("智能体 {} 返回失败，准备重试（第 {} 次尝试）", agent.getName(), attempt);
                            Thread.sleep(RETRY_DELAY_MS * attempt);
                            continue;
                        }
                        return response;
                    }

                } catch (TimeoutException e) {
                    future.cancel(true);
                    logger.warn("智能体 {} 执行超时（第 {} 次尝试）", agent.getName(), attempt);
                    lastException = e;
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("智能体执行被中断: {}", agent.getName());
                return AgentResponse.failure("服务暂时不可用，请稍后再试。", context);
            } catch (Exception e) {
                logger.error("智能体 {} 执行异常（第 {} 次尝试）: {}", agent.getName(), attempt, e.getMessage());
                lastException = e;
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 所有重试都失败了，执行回退策略
        logger.error("智能体 {} 所有重试均失败，执行回退策略", agent.getName());
        return executeFallback(agent, input, context, lastException);
    }

    /**
     * 判断是否需要重试
     * @param response 智能体响应
     * @return 是否需要重试
     */
    private boolean shouldRetry(AgentResponse response) {
        if (!response.isSuccess()) {
            String result = response.getResult();
            // 网络错误、服务不可用等可重试的错误
            return result.contains("超时") || 
                   result.contains("服务不可用") || 
                   result.contains("连接失败") ||
                   result.contains("暂时");
        }
        return false;
    }

    /**
     * 执行回退策略
     * @param agent 智能体实例
     * @param input 用户输入
     * @param context 上下文信息
     * @param exception 最后一次异常
     * @return 回退响应
     */
    private AgentResponse executeFallback(Agent agent, String input, Map<String, Object> context, 
                                          Exception exception) {
        String agentName = agent.getName();
        
        // 根据智能体类型提供不同的回退响应
        String fallbackMessage = switch (agentName) {
            case "BookRecommendationAgent" -> 
                "书籍推荐服务暂时不可用，您可以尝试直接浏览我们的书籍分类。";
            case "OrderProcessingAgent" -> 
                "订单服务暂时不可用，请稍后重试或联系客服。";
            case "PromotionExpertAgent" -> 
                "促销信息服务暂时不可用，当前全场图书均有优惠活动。";
            case "UserServiceAgent" -> 
                "用户服务暂时不可用，请稍后重试。";
            default -> 
                "服务暂时不可用，请稍后再试。";
        };

        // 记录错误日志
        logger.error("智能体 {} 回退响应: {}, 异常: {}", agentName, fallbackMessage, 
                    exception != null ? exception.getMessage() : "无");

        return AgentResponse.failure(fallbackMessage, context);
    }

    /**
     * 尝试使用替代智能体
     * @param intent 意图
     * @param input 用户输入
     * @param context 上下文信息
     * @param excludeAgent 排除的智能体（失败的智能体）
     * @param registry 智能体注册中心
     * @return 替代智能体的执行结果
     */
    public AgentResponse tryAlternativeAgent(String intent, String input, Map<String, Object> context,
                                             String excludeAgent, AgentRegistry registry) {
        List<Agent> agents = registry.findAgentsByIntent(intent);
        
        for (Agent agent : agents) {
            if (!agent.getName().equals(excludeAgent)) {
                logger.info("尝试使用替代智能体: {} -> {}", excludeAgent, agent.getName());
                try {
                    return agent.execute(input, context);
                } catch (Exception e) {
                    logger.warn("替代智能体 {} 也执行失败: {}", agent.getName(), e.getMessage());
                }
            }
        }

        // 没有可用的替代智能体
        return AgentResponse.failure("当前服务繁忙，请稍后再试。", context);
    }

    /**
     * 处理特定类型的错误
     * @param errorType 错误类型
     * @param context 上下文信息
     * @return 错误响应
     */
    public AgentResponse handleError(ErrorType errorType, Map<String, Object> context) {
        String message = switch (errorType) {
            case USER_NOT_LOGGED_IN -> 
                "需要先登录才能使用此功能，请先登录账户。";
            case INSUFFICIENT_PERMISSIONS -> 
                "您没有权限执行此操作。";
            case RESOURCE_NOT_FOUND -> 
                "未找到相关资源。";
            case VALIDATION_ERROR -> 
                "请求参数无效，请检查输入。";
            case SERVICE_UNAVAILABLE -> 
                "服务暂时不可用，请稍后再试。";
            case TIMEOUT -> 
                "请求超时，请稍后重试。";
            case UNKNOWN -> 
                "处理请求时发生未知错误。";
        };

        return AgentResponse.failure(message, context);
    }

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        USER_NOT_LOGGED_IN,
        INSUFFICIENT_PERMISSIONS,
        RESOURCE_NOT_FOUND,
        VALIDATION_ERROR,
        SERVICE_UNAVAILABLE,
        TIMEOUT,
        UNKNOWN
    }

    /**
     * 关闭执行器服务
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}