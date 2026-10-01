package com.bookstore.agent.communication;

import com.bookstore.agent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体注册中心
 * 负责智能体的注册、注销和发现
 */
@Service
public class AgentRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistry.class);

    /**
     * 智能体名称到实例的映射
     */
    private final Map<String, Agent> agentMap = new ConcurrentHashMap<>();

    /**
     * 智能体名称到元数据的映射
     */
    private final Map<String, AgentMetadata> metadataMap = new ConcurrentHashMap<>();

    /**
     * 意图到智能体名称的映射（一个意图可以对应多个智能体）
     */
    private final Map<String, List<String>> intentMapping = new ConcurrentHashMap<>();

    /**
     * 所有已注册的智能体
     */
    private final List<Agent> agents;

    @Autowired
    public AgentRegistry(List<Agent> agents) {
        this.agents = agents;
    }

    @PostConstruct
    public void initialize() {
        // 注册所有智能体
        for (Agent agent : agents) {
            registerAgent(agent);
        }
        logger.info("智能体注册中心初始化完成，共注册 {} 个智能体", agentMap.size());
    }

    /**
     * 注册智能体
     * @param agent 智能体实例
     */
    public void registerAgent(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("智能体不能为空");
        }

        String agentName = agent.getName();
        
        // 如果智能体已存在，先注销
        if (agentMap.containsKey(agentName)) {
            unregisterAgent(agentName);
        }

        // 注册智能体
        agentMap.put(agentName, agent);

        // 创建元数据
        AgentMetadata metadata = AgentMetadata.builder()
                .agentName(agentName)
                .className(agent.getClass().getName())
                .registeredAt(java.time.LocalDateTime.now())
                .status(AgentStatus.ACTIVE)
                .build();
        metadataMap.put(agentName, metadata);

        logger.info("注册智能体: {}", agentName);
    }

    /**
     * 注销智能体
     * @param agentName 智能体名称
     * @return 是否注销成功
     */
    public boolean unregisterAgent(String agentName) {
        if (agentName == null || agentName.isEmpty()) {
            return false;
        }

        Agent removed = agentMap.remove(agentName);
        metadataMap.remove(agentName);

        // 清理意图映射
        intentMapping.values().forEach(list -> list.remove(agentName));

        if (removed != null) {
            logger.info("注销智能体: {}", agentName);
            return true;
        }

        return false;
    }

    /**
     * 根据名称获取智能体
     * @param agentName 智能体名称
     * @return 智能体实例，如果没有找到返回 null
     */
    public Agent getAgent(String agentName) {
        if (agentName == null || agentName.isEmpty()) {
            return null;
        }

        Agent agent = agentMap.get(agentName);
        
        // 检查智能体状态
        if (agent != null) {
            AgentMetadata metadata = metadataMap.get(agentName);
            if (metadata != null && metadata.getStatus() == AgentStatus.INACTIVE) {
                logger.warn("智能体 {} 当前处于非活跃状态", agentName);
            }
        }

        return agent;
    }

    /**
     * 根据意图查找智能体
     * @param intent 意图字符串
     * @return 智能体列表
     */
    public List<Agent> findAgentsByIntent(String intent) {
        if (intent == null || intent.isEmpty()) {
            return Collections.emptyList();
        }

        List<Agent> result = new ArrayList<>();

        // 首先检查意图映射
        List<String> agentNames = intentMapping.get(intent);
        if (agentNames != null) {
            for (String agentName : agentNames) {
                Agent agent = agentMap.get(agentName);
                if (agent != null) {
                    result.add(agent);
                }
            }
        }

        // 如果意图映射中没有找到，遍历所有智能体查找
        if (result.isEmpty()) {
            for (Agent agent : agentMap.values()) {
                if (agent.canHandle(intent)) {
                    result.add(agent);
                    // 建立意图映射缓存
                    intentMapping.computeIfAbsent(intent, k -> new ArrayList<>()).add(agent.getName());
                }
            }
        }

        return result;
    }

    /**
     * 获取第一个能处理该意图的智能体
     * @param intent 意图字符串
     * @return 智能体实例，如果没有找到返回 null
     */
    public Agent findFirstAgentByIntent(String intent) {
        List<Agent> agents = findAgentsByIntent(intent);
        return agents.isEmpty() ? null : agents.get(0);
    }

    /**
     * 获取所有已注册的智能体
     * @return 智能体列表
     */
    public List<Agent> getAllAgents() {
        return new ArrayList<>(agentMap.values());
    }

    /**
     * 获取所有智能体名称
     * @return 智能体名称列表
     */
    public List<String> getAllAgentNames() {
        return new ArrayList<>(agentMap.keySet());
    }

    /**
     * 获取智能体元数据
     * @param agentName 智能体名称
     * @return 元数据对象，如果没有找到返回 null
     */
    public AgentMetadata getAgentMetadata(String agentName) {
        return metadataMap.get(agentName);
    }

    /**
     * 更新智能体状态
     * @param agentName 智能体名称
     * @param status 新状态
     * @return 是否更新成功
     */
    public boolean updateAgentStatus(String agentName, AgentStatus status) {
        AgentMetadata metadata = metadataMap.get(agentName);
        if (metadata != null) {
            metadata.setStatus(status);
            metadata.setUpdatedAt(java.time.LocalDateTime.now());
            logger.info("更新智能体状态: {} -> {}", agentName, status);
            return true;
        }
        return false;
    }

    /**
     * 获取智能体数量
     * @return 智能体数量
     */
    public int getAgentCount() {
        return agentMap.size();
    }

    /**
     * 检查智能体是否已注册
     * @param agentName 智能体名称
     * @return 如果已注册返回 true，否则返回 false
     */
    public boolean isAgentRegistered(String agentName) {
        return agentMap.containsKey(agentName);
    }

    /**
     * 智能体状态枚举
     */
    public enum AgentStatus {
        ACTIVE,
        INACTIVE,
        ERROR,
        MAINTENANCE
    }

    /**
     * 智能体元数据类
     */
    public static class AgentMetadata {
        private String agentName;
        private String className;
        private AgentStatus status;
        private java.time.LocalDateTime registeredAt;
        private java.time.LocalDateTime updatedAt;
        private int executionCount;
        private long totalExecutionTime;

        public AgentMetadata() {}

        public AgentMetadata(String agentName, String className, AgentStatus status,
                           java.time.LocalDateTime registeredAt, java.time.LocalDateTime updatedAt,
                           int executionCount, long totalExecutionTime) {
            this.agentName = agentName;
            this.className = className;
            this.status = status;
            this.registeredAt = registeredAt;
            this.updatedAt = updatedAt;
            this.executionCount = executionCount;
            this.totalExecutionTime = totalExecutionTime;
        }

        // Getter 和 Setter
        public String getAgentName() { return agentName; }
        public void setAgentName(String agentName) { this.agentName = agentName; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public AgentStatus getStatus() { return status; }
        public void setStatus(AgentStatus status) { this.status = status; }
        public java.time.LocalDateTime getRegisteredAt() { return registeredAt; }
        public void setRegisteredAt(java.time.LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public int getExecutionCount() { return executionCount; }
        public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public void setTotalExecutionTime(long totalExecutionTime) { this.totalExecutionTime = totalExecutionTime; }

        /**
         * 记录执行次数
         */
        public void incrementExecutionCount() {
            this.executionCount++;
        }

        /**
         * 记录执行时间
         */
        public void addExecutionTime(long millis) {
            this.totalExecutionTime += millis;
        }

        /**
         * 获取平均执行时间
         */
        public long getAverageExecutionTime() {
            return executionCount > 0 ? totalExecutionTime / executionCount : 0;
        }

        /**
         * Builder 静态内部类
         */
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final AgentMetadata metadata = new AgentMetadata();

            public Builder agentName(String agentName) { metadata.agentName = agentName; return this; }
            public Builder className(String className) { metadata.className = className; return this; }
            public Builder status(AgentStatus status) { metadata.status = status; return this; }
            public Builder registeredAt(java.time.LocalDateTime registeredAt) { metadata.registeredAt = registeredAt; return this; }
            public Builder updatedAt(java.time.LocalDateTime updatedAt) { metadata.updatedAt = updatedAt; return this; }
            public Builder executionCount(int executionCount) { metadata.executionCount = executionCount; return this; }
            public Builder totalExecutionTime(long totalExecutionTime) { metadata.totalExecutionTime = totalExecutionTime; return this; }

            public AgentMetadata build() { return metadata; }
        }
    }
}