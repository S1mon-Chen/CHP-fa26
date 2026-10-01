package com.bookstore.agent.skill;

import com.bookstore.agent.rule.Rule;
import com.bookstore.agent.rule.RuleEngine;
import com.bookstore.agent.skill.SkillRegistry.SkillMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 技能执行器
 * 
 * 负责技能的执行、链调用管理和规则检查。
 * 
 * 核心功能：
 * 1. 执行单个技能
 * 2. 执行技能链（支持链式调用）
 * 3. 在执行前检查规则条件
 * 4. 管理执行上下文
 */
@Service
public class SkillExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SkillExecutor.class);

    /**
     * 最大技能调用次数（防止无限循环）
     */
    private static final int MAX_SKILL_CALLS = 10;

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private RuleEngine ruleEngine;

    /**
     * 执行技能
     * @param skillName 技能名称
     * @param input 输入参数
     * @param context 上下文信息
     * @return 技能执行结果
     */
    public SkillResult executeSkill(String skillName, String input, Map<String, Object> context) {
        logger.info("执行技能: {}, 输入: {}", skillName, input);

        // 获取技能
        Skill skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            logger.warn("技能 {} 不存在", skillName);
            return SkillResult.failure("技能 " + skillName + " 不存在");
        }

        // 检查技能状态
        SkillMetadata metadata = skillRegistry.getSkillMetadata(skillName);
        if (metadata != null && metadata.getStatus() != SkillRegistry.SkillStatus.ACTIVE) {
            logger.warn("技能 {} 当前处于非活跃状态", skillName);
            return SkillResult.failure("技能 " + skillName + " 当前不可用");
        }

        // 检查前置条件
        if (!skill.canExecute(context)) {
            logger.warn("技能 {} 无法执行，前置条件不满足", skillName);
            return SkillResult.failure("技能 " + skillName + " 无法执行，前置条件不满足");
        }

        // 执行技能
        long startTime = System.currentTimeMillis();
        SkillResult result = skill.execute(input, context);
        long executionTime = System.currentTimeMillis() - startTime;

        // 更新技能元数据
        if (metadata != null) {
            metadata.incrementExecutionCount();
            metadata.addExecutionTime(executionTime);
        }

        logger.info("技能 {} 执行完成，耗时: {}ms，成功: {}", skillName, executionTime, result.isSuccess());

        // 如果有下一个技能，继续执行
        if (result.isSuccess() && result.getNextSkill() != null && !result.getNextSkill().isEmpty()) {
            Map<String, Object> newContext = result.getContext();
            if (newContext == null) {
                newContext = context != null ? context : new HashMap<>();
            }
            SkillResult nextResult = executeSkillChain(result.getNextSkill(), input, newContext, 1);
            
            if (nextResult.isSuccess()) {
                return SkillResult.success(result.getData() + "\n\n" + nextResult.getData(), 
                        nextResult.getContext());
            }
        }

        return result;
    }

    /**
     * 执行技能链
     * @param skillName 技能名称
     * @param input 输入参数
     * @param context 上下文信息
     * @param callCount 当前调用次数
     * @return 技能执行结果
     */
    private SkillResult executeSkillChain(String skillName, String input, 
                                         Map<String, Object> context, int callCount) {
        // 防止无限循环
        if (callCount >= MAX_SKILL_CALLS) {
            logger.warn("技能调用次数超过限制: {}", MAX_SKILL_CALLS);
            return SkillResult.failure("技能调用次数超过限制");
        }

        // 获取技能
        Skill skill = skillRegistry.getSkill(skillName);
        if (skill == null) {
            logger.warn("技能链中的技能 {} 不存在", skillName);
            return SkillResult.failure("技能 " + skillName + " 不存在");
        }

        // 检查前置条件
        if (!skill.canExecute(context)) {
            logger.warn("技能链中的技能 {} 无法执行", skillName);
            return SkillResult.failure("技能 " + skillName + " 无法执行");
        }

        // 执行技能
        SkillResult result = skill.execute(input, context);

        // 更新上下文
        if (result.getContext() != null) {
            context.putAll(result.getContext());
        }

        // 如果有下一个技能，继续执行
        if (result.isSuccess() && result.getNextSkill() != null && !result.getNextSkill().isEmpty()) {
            SkillResult nextResult = executeSkillChain(result.getNextSkill(), input, context, callCount + 1);
            
            if (nextResult.isSuccess()) {
                return SkillResult.success(result.getData() + "\n\n" + nextResult.getData(), 
                        nextResult.getContext());
            }
        }

        return result;
    }

    /**
     * 根据规则执行技能
     * @param skillName 技能名称
     * @param input 输入参数
     * @param context 上下文信息
     * @param requiredRules 必须满足的规则列表
     * @return 技能执行结果
     */
    public SkillResult executeSkillWithRules(String skillName, String input, 
                                            Map<String, Object> context, 
                                            List<String> requiredRules) {
        // 检查所有规则
        if (requiredRules != null && !requiredRules.isEmpty()) {
            logger.info("检查规则: {}", requiredRules);
            
            boolean allRulesPassed = ruleEngine.evaluateAll(requiredRules, context);
            
            if (!allRulesPassed) {
                logger.warn("规则检查失败");
                return SkillResult.failure("规则检查失败，无法执行技能");
            }
        }

        // 执行技能
        return executeSkill(skillName, input, context);
    }

    /**
     * 获取所有可用技能
     * @return 技能列表
     */
    public List<Skill> getAllSkills() {
        return skillRegistry.getAllSkills();
    }

    /**
     * 获取所有可用技能名称
     * @return 技能名称列表
     */
    public List<String> getAllSkillNames() {
        return skillRegistry.getAllSkillNames();
    }

    /**
     * 获取技能信息
     * @param skillName 技能名称
     * @return 技能元数据
     */
    public SkillMetadata getSkillInfo(String skillName) {
        return skillRegistry.getSkillMetadata(skillName);
    }

    /**
     * 获取技能类别列表
     * @return 类别列表
     */
    public List<String> getSkillCategories() {
        return skillRegistry.getAllCategories();
    }

    /**
     * 根据类别获取技能
     * @param category 技能类别
     * @return 技能列表
     */
    public List<Skill> getSkillsByCategory(String category) {
        return skillRegistry.findSkillsByCategory(category);
    }

    /**
     * 创建技能执行上下文
     * @param userId 用户ID（可选）
     * @return 上下文对象
     */
    public Map<String, Object> createContext(Long userId) {
        Map<String, Object> context = new HashMap<>();
        if (userId != null) {
            context.put("userId", userId);
        }
        context.put("timestamp", System.currentTimeMillis());
        return context;
    }
}