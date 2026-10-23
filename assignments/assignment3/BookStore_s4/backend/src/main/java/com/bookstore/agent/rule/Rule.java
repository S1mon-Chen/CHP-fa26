package com.bookstore.agent.rule;

import java.util.Map;

/**
 * 规则接口定义
 * 
 * Rule（规则）是一个条件判断单元，用于决定是否执行某个动作。
 * 规则可以组合使用，形成复杂的业务逻辑。
 * 
 * 规则的特点：
 * 1. 条件判断：根据上下文判断是否满足条件
 * 2. 可组合：多个规则可以组合成规则集
 * 3. 可配置：规则参数可以配置
 * 4. 无状态：规则本身不维护状态
 */
public interface Rule {

    /**
     * 获取规则名称
     * @return 规则名称
     */
    String getName();

    /**
     * 获取规则描述
     * @return 规则描述
     */
    String getDescription();

    /**
     * 评估规则是否满足条件
     * @param context 上下文信息
     * @return 如果满足条件返回 true，否则返回 false
     */
    boolean evaluate(Map<String, Object> context);

    /**
     * 获取规则优先级（数字越小优先级越高）
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
}