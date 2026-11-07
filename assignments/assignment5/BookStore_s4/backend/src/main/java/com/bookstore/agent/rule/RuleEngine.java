package com.bookstore.agent.rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 规则引擎
 * 
 * 负责规则的管理、评估和执行。
 * 支持规则的注册、注销、组合和评估。
 * 
 * 规则引擎的核心功能：
 * 1. 规则注册与管理
 * 2. 规则评估（单条规则和规则集）
 * 3. 规则组合（AND、OR、NOT）
 * 4. 规则优先级排序
 */
@Service
public class RuleEngine {

    private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);

    /**
     * 规则名称到规则实例的映射
     */
    private final Map<String, Rule> ruleMap = new ConcurrentHashMap<>();

    /**
     * 规则集名称到规则列表的映射
     */
    private final Map<String, List<String>> ruleSetMapping = new ConcurrentHashMap<>();

    /**
     * 所有已注册的规则
     */
    private final List<Rule> rules;

    @Autowired
    public RuleEngine(List<Rule> rules) {
        this.rules = rules;
    }

    @PostConstruct
    public void initialize() {
        for (Rule rule : rules) {
            registerRule(rule);
        }
        logger.info("规则引擎初始化完成，共注册 {} 条规则", ruleMap.size());
    }

    /**
     * 注册规则
     * @param rule 规则实例
     */
    public void registerRule(Rule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("规则不能为空");
        }

        String ruleName = rule.getName();

        // 如果规则已存在，先注销
        if (ruleMap.containsKey(ruleName)) {
            unregisterRule(ruleName);
        }

        // 注册规则
        ruleMap.put(ruleName, rule);
        logger.info("注册规则: {}", ruleName);
    }

    /**
     * 注销规则
     * @param ruleName 规则名称
     * @return 是否注销成功
     */
    public boolean unregisterRule(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return false;
        }

        Rule removed = ruleMap.remove(ruleName);

        // 清理规则集映射
        ruleSetMapping.values().forEach(list -> list.remove(ruleName));

        if (removed != null) {
            logger.info("注销规则: {}", ruleName);
            return true;
        }

        return false;
    }

    /**
     * 根据名称获取规则
     * @param ruleName 规则名称
     * @return 规则实例，如果没有找到返回 null
     */
    public Rule getRule(String ruleName) {
        if (ruleName == null || ruleName.isEmpty()) {
            return null;
        }
        return ruleMap.get(ruleName);
    }

    /**
     * 评估单条规则
     * @param ruleName 规则名称
     * @param context 上下文信息
     * @return 如果满足条件返回 true，否则返回 false
     */
    public boolean evaluateRule(String ruleName, Map<String, Object> context) {
        Rule rule = getRule(ruleName);
        if (rule == null) {
            logger.warn("规则 {} 不存在", ruleName);
            return false;
        }

        boolean result = rule.evaluate(context);
        logger.debug("规则 {} 评估结果: {}", ruleName, result);
        return result;
    }

    /**
     * 评估多条规则（AND逻辑）
     * @param ruleNames 规则名称列表
     * @param context 上下文信息
     * @return 如果所有规则都满足返回 true，否则返回 false
     */
    public boolean evaluateAll(List<String> ruleNames, Map<String, Object> context) {
        if (ruleNames == null || ruleNames.isEmpty()) {
            return true;
        }

        for (String ruleName : ruleNames) {
            if (!evaluateRule(ruleName, context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估多条规则（OR逻辑）
     * @param ruleNames 规则名称列表
     * @param context 上下文信息
     * @return 如果任意规则满足返回 true，否则返回 false
     */
    public boolean evaluateAny(List<String> ruleNames, Map<String, Object> context) {
        if (ruleNames == null || ruleNames.isEmpty()) {
            return false;
        }

        for (String ruleName : ruleNames) {
            if (evaluateRule(ruleName, context)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建规则集
     * @param ruleSetName 规则集名称
     * @param ruleNames 规则名称列表
     */
    public void createRuleSet(String ruleSetName, List<String> ruleNames) {
        if (ruleSetName == null || ruleSetName.isEmpty()) {
            throw new IllegalArgumentException("规则集名称不能为空");
        }

        // 验证所有规则是否存在
        for (String ruleName : ruleNames) {
            if (!ruleMap.containsKey(ruleName)) {
                throw new IllegalArgumentException("规则 " + ruleName + " 不存在");
            }
        }

        ruleSetMapping.put(ruleSetName, new ArrayList<>(ruleNames));
        logger.info("创建规则集: {}，包含 {} 条规则", ruleSetName, ruleNames.size());
    }

    /**
     * 评估规则集（AND逻辑）
     * @param ruleSetName 规则集名称
     * @param context 上下文信息
     * @return 如果所有规则都满足返回 true，否则返回 false
     */
    public boolean evaluateRuleSet(String ruleSetName, Map<String, Object> context) {
        List<String> ruleNames = ruleSetMapping.get(ruleSetName);
        if (ruleNames == null) {
            logger.warn("规则集 {} 不存在", ruleSetName);
            return false;
        }

        return evaluateAll(ruleNames, context);
    }

    /**
     * 评估规则集（OR逻辑）
     * @param ruleSetName 规则集名称
     * @param context 上下文信息
     * @return 如果任意规则满足返回 true，否则返回 false
     */
    public boolean evaluateRuleSetOr(String ruleSetName, Map<String, Object> context) {
        List<String> ruleNames = ruleSetMapping.get(ruleSetName);
        if (ruleNames == null) {
            logger.warn("规则集 {} 不存在", ruleSetName);
            return false;
        }

        return evaluateAny(ruleNames, context);
    }

    /**
     * 获取所有已注册的规则
     * @return 规则列表
     */
    public List<Rule> getAllRules() {
        return new ArrayList<>(ruleMap.values());
    }

    /**
     * 获取所有规则名称
     * @return 规则名称列表
     */
    public List<String> getAllRuleNames() {
        return new ArrayList<>(ruleMap.keySet());
    }

    /**
     * 获取所有规则集名称
     * @return 规则集名称列表
     */
    public List<String> getAllRuleSets() {
        return new ArrayList<>(ruleSetMapping.keySet());
    }

    /**
     * 获取规则数量
     * @return 规则数量
     */
    public int getRuleCount() {
        return ruleMap.size();
    }

    /**
     * 检查规则是否已注册
     * @param ruleName 规则名称
     * @return 如果已注册返回 true，否则返回 false
     */
    public boolean isRuleRegistered(String ruleName) {
        return ruleMap.containsKey(ruleName);
    }

    /**
     * 获取排序后的规则列表（按优先级）
     * @return 排序后的规则列表
     */
    public List<Rule> getRulesSortedByPriority() {
        return ruleMap.values().stream()
                .sorted(Comparator.comparingInt(Rule::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * 创建组合规则（AND）
     * @param rules 规则列表
     * @return 组合规则
     */
    public Rule createAndRule(List<Rule> rules) {
        return new CompositeRule("AND_" + System.currentTimeMillis(), 
                "组合规则(AND)", rules, CompositeRule.CombinationType.AND);
    }

    /**
     * 创建组合规则（OR）
     * @param rules 规则列表
     * @return 组合规则
     */
    public Rule createOrRule(List<Rule> rules) {
        return new CompositeRule("OR_" + System.currentTimeMillis(), 
                "组合规则(OR)", rules, CompositeRule.CombinationType.OR);
    }

    /**
     * 创建否定规则
     * @param rule 原始规则
     * @return 否定规则
     */
    public Rule createNotRule(Rule rule) {
        return new NegationRule("NOT_" + rule.getName(), "否定规则: " + rule.getDescription(), rule);
    }

    /**
     * 组合规则实现
     */
    public static class CompositeRule implements Rule {
        private final String name;
        private final String description;
        private final List<Rule> rules;
        private final CombinationType combinationType;

        public enum CombinationType {
            AND, OR
        }

        public CompositeRule(String name, String description, List<Rule> rules, CombinationType combinationType) {
            this.name = name;
            this.description = description;
            this.rules = rules;
            this.combinationType = combinationType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public boolean evaluate(Map<String, Object> context) {
            if (rules == null || rules.isEmpty()) {
                return true;
            }

            if (combinationType == CombinationType.AND) {
                return rules.stream().allMatch(rule -> rule.evaluate(context));
            } else {
                return rules.stream().anyMatch(rule -> rule.evaluate(context));
            }
        }
    }

    /**
     * 否定规则实现
     */
    public static class NegationRule implements Rule {
        private final String name;
        private final String description;
        private final Rule rule;

        public NegationRule(String name, String description, Rule rule) {
            this.name = name;
            this.description = description;
            this.rule = rule;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public boolean evaluate(Map<String, Object> context) {
            return !rule.evaluate(context);
        }
    }
}