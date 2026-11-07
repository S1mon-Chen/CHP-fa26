package com.bookstore.agent.skill;

import java.util.Map;

/**
 * 技能接口定义
 * 
 * Skill（技能）是智能体可以调用的可复用能力单元。
 * 每个技能封装了一个特定的功能，可以独立执行，也可以组合使用。
 * 
 * 技能的特点：
 * 1. 原子性：每个技能完成一个特定的、独立的任务
 * 2. 可复用：同一个技能可以被多个智能体调用
 * 3. 组合性：多个技能可以组合成更复杂的业务流程
 * 4. 可配置：技能可以通过参数进行配置
 */
public interface Skill {

    /**
     * 获取技能名称
     * @return 技能名称
     */
    String getName();

    /**
     * 获取技能描述
     * @return 技能描述
     */
    String getDescription();

    /**
     * 获取技能所属类别
     * @return 技能类别
     */
    String getCategory();

    /**
     * 判断技能是否可执行（前置条件检查）
     * @param context 上下文信息
     * @return 如果可以执行返回 true
     */
    boolean canExecute(Map<String, Object> context);

    /**
     * 执行技能
     * @param input 输入参数
     * @param context 上下文信息
     * @return 技能执行结果
     */
    SkillResult execute(String input, Map<String, Object> context);

    /**
     * 获取技能所需的参数列表
     * @return 参数名称列表
     */
    java.util.List<String> getRequiredParameters();
}