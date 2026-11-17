package com.bookstore.agent.skill;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能执行结果
 * 
 * 封装技能执行的结果信息，包括：
 * - 执行是否成功
 * - 返回的数据
 * - 错误信息（如果失败）
 * - 上下文更新
 */
public class SkillResult {

    /**
     * 执行是否成功
     */
    private boolean success;

    /**
     * 返回的数据
     */
    private Object data;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 更新后的上下文
     */
    private Map<String, Object> context;

    /**
     * 推荐的下一个技能（用于技能链）
     */
    private String nextSkill;

    private SkillResult(boolean success, Object data, String errorMessage, 
                       Map<String, Object> context, String nextSkill) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.context = context;
        this.nextSkill = nextSkill;
    }

    /**
     * 创建成功的结果
     * @param data 返回的数据
     * @return SkillResult 实例
     */
    public static SkillResult success(Object data) {
        return new SkillResult(true, data, null, null, null);
    }

    /**
     * 创建成功的结果（带上下文）
     * @param data 返回的数据
     * @param context 更新后的上下文
     * @return SkillResult 实例
     */
    public static SkillResult success(Object data, Map<String, Object> context) {
        return new SkillResult(true, data, null, context, null);
    }

    /**
     * 创建成功的结果（带上下文和下一个技能）
     * @param data 返回的数据
     * @param context 更新后的上下文
     * @param nextSkill 下一个技能名称
     * @return SkillResult 实例
     */
    public static SkillResult success(Object data, Map<String, Object> context, String nextSkill) {
        return new SkillResult(true, data, null, context, nextSkill);
    }

    /**
     * 创建失败的结果
     * @param errorMessage 错误信息
     * @return SkillResult 实例
     */
    public static SkillResult failure(String errorMessage) {
        return new SkillResult(false, null, errorMessage, null, null);
    }

    /**
     * 创建失败的结果（带上下文）
     * @param errorMessage 错误信息
     * @param context 当前上下文
     * @return SkillResult 实例
     */
    public static SkillResult failure(String errorMessage, Map<String, Object> context) {
        return new SkillResult(false, null, errorMessage, context, null);
    }

    // Getter方法
    public boolean isSuccess() {
        return success;
    }

    public Object getData() {
        return data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public String getNextSkill() {
        return nextSkill;
    }

    /**
     * 将结果转换为字符串
     */
    @Override
    public String toString() {
        if (success) {
            return data != null ? data.toString() : "执行成功";
        } else {
            return "执行失败: " + errorMessage;
        }
    }
}