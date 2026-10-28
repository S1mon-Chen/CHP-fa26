package com.bookstore.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册中心
 * 
 * 负责技能的注册、注销、查找和管理。
 * 提供技能的元数据管理和状态跟踪。
 */
@Service
public class SkillRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SkillRegistry.class);

    /**
     * 技能名称到技能实例的映射
     */
    private final Map<String, Skill> skillMap = new ConcurrentHashMap<>();

    /**
     * 技能名称到元数据的映射
     */
    private final Map<String, SkillMetadata> metadataMap = new ConcurrentHashMap<>();

    /**
     * 类别到技能名称列表的映射
     */
    private final Map<String, List<String>> categoryMapping = new ConcurrentHashMap<>();

    /**
     * 所有已注册的技能
     */
    private final List<Skill> skills;

    @Autowired
    public SkillRegistry(List<Skill> skills) {
        this.skills = skills;
    }

    @PostConstruct
    public void initialize() {
        for (Skill skill : skills) {
            registerSkill(skill);
        }
        logger.info("技能注册中心初始化完成，共注册 {} 个技能", skillMap.size());
    }

    /**
     * 注册技能
     * @param skill 技能实例
     */
    public void registerSkill(Skill skill) {
        if (skill == null) {
            throw new IllegalArgumentException("技能不能为空");
        }

        String skillName = skill.getName();

        // 如果技能已存在，先注销
        if (skillMap.containsKey(skillName)) {
            unregisterSkill(skillName);
        }

        // 注册技能
        skillMap.put(skillName, skill);

        // 创建元数据
        SkillMetadata metadata = SkillMetadata.builder()
                .skillName(skillName)
                .description(skill.getDescription())
                .category(skill.getCategory())
                .className(skill.getClass().getName())
                .registeredAt(java.time.LocalDateTime.now())
                .status(SkillStatus.ACTIVE)
                .requiredParameters(skill.getRequiredParameters())
                .build();
        metadataMap.put(skillName, metadata);

        // 更新类别映射
        categoryMapping.computeIfAbsent(skill.getCategory(), k -> new ArrayList<>()).add(skillName);

        logger.info("注册技能: {} ({})", skillName, skill.getCategory());
    }

    /**
     * 注销技能
     * @param skillName 技能名称
     * @return 是否注销成功
     */
    public boolean unregisterSkill(String skillName) {
        if (skillName == null || skillName.isEmpty()) {
            return false;
        }

        Skill removed = skillMap.remove(skillName);
        SkillMetadata metadata = metadataMap.remove(skillName);

        // 清理类别映射
        if (metadata != null) {
            List<String> categorySkills = categoryMapping.get(metadata.getCategory());
            if (categorySkills != null) {
                categorySkills.remove(skillName);
            }
        }

        if (removed != null) {
            logger.info("注销技能: {}", skillName);
            return true;
        }

        return false;
    }

    /**
     * 根据名称获取技能
     * @param skillName 技能名称
     * @return 技能实例，如果没有找到返回 null
     */
    public Skill getSkill(String skillName) {
        if (skillName == null || skillName.isEmpty()) {
            return null;
        }

        Skill skill = skillMap.get(skillName);

        // 检查技能状态
        if (skill != null) {
            SkillMetadata metadata = metadataMap.get(skillName);
            if (metadata != null && metadata.getStatus() == SkillStatus.INACTIVE) {
                logger.warn("技能 {} 当前处于非活跃状态", skillName);
            }
        }

        return skill;
    }

    /**
     * 根据类别查找技能
     * @param category 技能类别
     * @return 技能列表
     */
    public List<Skill> findSkillsByCategory(String category) {
        if (category == null || category.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> skillNames = categoryMapping.get(category);
        if (skillNames == null) {
            return Collections.emptyList();
        }

        List<Skill> result = new ArrayList<>();
        for (String skillName : skillNames) {
            Skill skill = skillMap.get(skillName);
            if (skill != null) {
                result.add(skill);
            }
        }

        return result;
    }

    /**
     * 获取所有已注册的技能
     * @return 技能列表
     */
    public List<Skill> getAllSkills() {
        return new ArrayList<>(skillMap.values());
    }

    /**
     * 获取所有技能名称
     * @return 技能名称列表
     */
    public List<String> getAllSkillNames() {
        return new ArrayList<>(skillMap.keySet());
    }

    /**
     * 获取技能元数据
     * @param skillName 技能名称
     * @return 元数据对象，如果没有找到返回 null
     */
    public SkillMetadata getSkillMetadata(String skillName) {
        return metadataMap.get(skillName);
    }

    /**
     * 更新技能状态
     * @param skillName 技能名称
     * @param status 新状态
     * @return 是否更新成功
     */
    public boolean updateSkillStatus(String skillName, SkillStatus status) {
        SkillMetadata metadata = metadataMap.get(skillName);
        if (metadata != null) {
            metadata.setStatus(status);
            metadata.setUpdatedAt(java.time.LocalDateTime.now());
            logger.info("更新技能状态: {} -> {}", skillName, status);
            return true;
        }
        return false;
    }

    /**
     * 获取技能数量
     * @return 技能数量
     */
    public int getSkillCount() {
        return skillMap.size();
    }

    /**
     * 检查技能是否已注册
     * @param skillName 技能名称
     * @return 如果已注册返回 true，否则返回 false
     */
    public boolean isSkillRegistered(String skillName) {
        return skillMap.containsKey(skillName);
    }

    /**
     * 获取所有技能类别
     * @return 类别列表
     */
    public List<String> getAllCategories() {
        return new ArrayList<>(categoryMapping.keySet());
    }

    /**
     * 技能状态枚举
     */
    public enum SkillStatus {
        ACTIVE,
        INACTIVE,
        ERROR,
        MAINTENANCE
    }

    /**
     * 技能元数据类
     */
    public static class SkillMetadata {
        private String skillName;
        private String description;
        private String category;
        private String className;
        private SkillStatus status;
        private java.time.LocalDateTime registeredAt;
        private java.time.LocalDateTime updatedAt;
        private int executionCount;
        private long totalExecutionTime;
        private List<String> requiredParameters;

        public SkillMetadata() {}

        public SkillMetadata(String skillName, String description, String category, String className,
                            SkillStatus status, java.time.LocalDateTime registeredAt, 
                            java.time.LocalDateTime updatedAt, int executionCount, 
                            long totalExecutionTime, List<String> requiredParameters) {
            this.skillName = skillName;
            this.description = description;
            this.category = category;
            this.className = className;
            this.status = status;
            this.registeredAt = registeredAt;
            this.updatedAt = updatedAt;
            this.executionCount = executionCount;
            this.totalExecutionTime = totalExecutionTime;
            this.requiredParameters = requiredParameters;
        }

        // Getter 和 Setter
        public String getSkillName() { return skillName; }
        public void setSkillName(String skillName) { this.skillName = skillName; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public SkillStatus getStatus() { return status; }
        public void setStatus(SkillStatus status) { this.status = status; }
        public java.time.LocalDateTime getRegisteredAt() { return registeredAt; }
        public void setRegisteredAt(java.time.LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
        public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public int getExecutionCount() { return executionCount; }
        public void setExecutionCount(int executionCount) { this.executionCount = executionCount; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public void setTotalExecutionTime(long totalExecutionTime) { this.totalExecutionTime = totalExecutionTime; }
        public List<String> getRequiredParameters() { return requiredParameters; }
        public void setRequiredParameters(List<String> requiredParameters) { this.requiredParameters = requiredParameters; }

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
            private final SkillMetadata metadata = new SkillMetadata();

            public Builder skillName(String skillName) { metadata.skillName = skillName; return this; }
            public Builder description(String description) { metadata.description = description; return this; }
            public Builder category(String category) { metadata.category = category; return this; }
            public Builder className(String className) { metadata.className = className; return this; }
            public Builder status(SkillStatus status) { metadata.status = status; return this; }
            public Builder registeredAt(java.time.LocalDateTime registeredAt) { metadata.registeredAt = registeredAt; return this; }
            public Builder updatedAt(java.time.LocalDateTime updatedAt) { metadata.updatedAt = updatedAt; return this; }
            public Builder executionCount(int executionCount) { metadata.executionCount = executionCount; return this; }
            public Builder totalExecutionTime(long totalExecutionTime) { metadata.totalExecutionTime = totalExecutionTime; return this; }
            public Builder requiredParameters(List<String> requiredParameters) { metadata.requiredParameters = requiredParameters; return this; }

            public SkillMetadata build() { return metadata; }
        }
    }
}