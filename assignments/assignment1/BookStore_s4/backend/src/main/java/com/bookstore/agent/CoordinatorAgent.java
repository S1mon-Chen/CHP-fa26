package com.bookstore.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 协调器智能体
 * 作为多智能体系统的核心，负责：
 * 1. 意图识别
 * 2. 智能体选择与调度
 * 3. 多智能体协作编排
 * 4. 结果汇总
 */
@Service
public class CoordinatorAgent {

    private static final Logger logger = LoggerFactory.getLogger(CoordinatorAgent.class);

    /**
     * 所有注册的智能体列表
     */
    private final List<Agent> agents;

    /**
     * 智能体名称到实例的映射
     */
    private final Map<String, Agent> agentMap;

    /**
     * 最大智能体调用次数（防止无限循环）
     */
    private static final int MAX_AGENT_CALLS = 5;

    @Autowired
    public CoordinatorAgent(List<Agent> agents) {
        this.agents = agents;
        this.agentMap = new HashMap<>();
        for (Agent agent : agents) {
            agentMap.put(agent.getName(), agent);
            logger.info("注册智能体: {}", agent.getName());
        }
    }

    /**
     * 处理用户请求的主入口方法
     * @param userQuery 用户输入
     * @param userId 用户ID（可选）
     * @return 最终响应结果
     */
    public String handleRequest(String userQuery, Long userId) {
        logger.info("收到用户请求: {}, 用户ID: {}", userQuery, userId);

        // 初始化上下文
        Map<String, Object> context = new HashMap<>();
        if (userId != null) {
            context.put("userId", userId);
        }
        context.put("originalQuery", userQuery);
        context.put("timestamp", System.currentTimeMillis());

        try {
            // 第一步：意图识别
            String intent = analyzeIntent(userQuery);
            logger.info("识别意图: {}", intent);

            // 第二步：查找合适的智能体（传递原始用户输入，让智能体自己判断）
            Agent agent = findAgent(userQuery);
            
            if (agent == null) {
                logger.warn("未找到能处理意图 '{}' 的智能体", intent);
                return "抱歉，我无法理解您的请求。您可以尝试询问书籍推荐、订单查询、促销优惠或个人信息相关的问题。";
            }

            logger.info("选择智能体: {}", agent.getName());

            // 第三步：执行智能体并处理链式调用
            String finalResult = executeAgentChain(agent, userQuery, context, 0);

            logger.info("请求处理完成，结果长度: {} 字符", finalResult.length());
            return finalResult;

        } catch (Exception e) {
            logger.error("协调器智能体执行异常: {}", e.getMessage(), e);
            return "抱歉，处理您的请求时出现了问题，请稍后再试。";
        }
    }

    /**
     * 分析用户意图
     * @param userQuery 用户输入
     * @return 意图字符串
     */
    private String analyzeIntent(String userQuery) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return "UNKNOWN";
        }

        String queryLower = userQuery.toLowerCase().trim();

        // 意图匹配规则
        if (queryLower.contains("推荐") || queryLower.contains("找书") || 
            queryLower.contains("想买") || queryLower.contains("搜索") ||
            queryLower.contains("什么书") || queryLower.contains("书籍")) {
            return "BOOK_RECOMMENDATION";
        }

        if (queryLower.contains("下单") || queryLower.contains("购买") || 
            queryLower.contains("订单") || queryLower.contains("取消") ||
            queryLower.contains("支付")) {
            return "ORDER_PROCESSING";
        }

        if (queryLower.contains("优惠") || queryLower.contains("折扣") || 
            queryLower.contains("促销") || queryLower.contains("优惠券") ||
            queryLower.contains("满减")) {
            return "PROMOTION";
        }

        if (queryLower.contains("我的") || queryLower.contains("账户") || 
            queryLower.contains("积分") || queryLower.contains("会员") ||
            queryLower.contains("个人信息") || queryLower.contains("资料")) {
            return "USER_SERVICE";
        }

        if (queryLower.contains("你好") || queryLower.contains("您好") || 
            queryLower.contains("嗨") || queryLower.contains("哈喽")) {
            return "GREETING";
        }

        if (queryLower.contains("谢谢") || queryLower.contains("感谢")) {
            return "THANKS";
        }

        return "UNKNOWN";
    }

    /**
     * 根据意图查找合适的智能体
     * @param intent 用户意图
     * @return 智能体实例，如果没有找到返回 null
     */
    private Agent findAgent(String intent) {
        // 遍历所有智能体，找到第一个能处理该意图的智能体
        for (Agent agent : agents) {
            if (agent.canHandle(intent)) {
                return agent;
            }
        }
        return null;
    }

    /**
     * 执行智能体链（支持链式调用）
     * @param agent 当前智能体
     * @param input 用户输入
     * @param context 上下文信息
     * @param callCount 当前调用次数
     * @return 最终结果
     */
    private String executeAgentChain(Agent agent, String input, Map<String, Object> context, int callCount) {
        // 防止无限循环
        if (callCount >= MAX_AGENT_CALLS) {
            logger.warn("智能体调用次数超过限制: {}", MAX_AGENT_CALLS);
            return "处理请求时出现问题，请简化您的查询或稍后再试。";
        }

        // 执行当前智能体
        AgentResponse response = agent.execute(input, context);
        logger.info("智能体 {} 执行完成，成功: {}, 下一个智能体: {}", 
                    agent.getName(), response.isSuccess(), response.getNextAgent());

        // 更新上下文
        if (response.getContext() != null) {
            context.putAll(response.getContext());
        }

        // 如果需要调用下一个智能体
        if (response.isSuccess() && response.getNextAgent() != null && !response.getNextAgent().isEmpty()) {
            Agent nextAgent = agentMap.get(response.getNextAgent());
            
            if (nextAgent != null) {
                logger.info("调用下一个智能体: {}", nextAgent.getName());
                String nextResult = executeAgentChain(nextAgent, input, context, callCount + 1);
                
                // 如果下一个智能体返回了结果，将两个结果合并
                if (nextResult != null && !nextResult.isEmpty()) {
                    return response.getResult() + "\n\n" + nextResult;
                }
            } else {
                logger.warn("找不到下一个智能体: {}", response.getNextAgent());
            }
        }

        return response.getResult();
    }

    /**
     * 处理问候语
     * @return 问候响应
     */
    private String handleGreeting() {
        return "您好！我是书店智能助手，请问有什么可以帮助您的？\n\n您可以：\n" +
               "- 询问书籍推荐\n" +
               "- 查询订单状态\n" +
               "- 了解促销活动\n" +
               "- 查看个人信息";
    }

    /**
     * 处理感谢语
     * @return 感谢响应
     */
    private String handleThanks() {
        return "不客气！如果您还有其他问题，随时可以问我。";
    }

    /**
     * 获取所有已注册的智能体名称
     * @return 智能体名称列表
     */
    public List<String> getRegisteredAgents() {
        List<String> agentNames = new ArrayList<>();
        for (Agent agent : agents) {
            agentNames.add(agent.getName());
        }
        return agentNames;
    }

    /**
     * 获取智能体数量
     * @return 智能体数量
     */
    public int getAgentCount() {
        return agents.size();
    }
}