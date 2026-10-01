package com.bookstore.controller;

import com.bookstore.agent.rule.Rule;
import com.bookstore.agent.rule.RuleEngine;
import com.bookstore.agent.skill.Skill;
import com.bookstore.agent.skill.SkillExecutor;
import com.bookstore.agent.skill.SkillRegistry;
import com.bookstore.agent.skill.SkillRegistry.SkillMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 技能管理控制器
 * 
 * 提供技能的查询、执行和规则评估功能。
 * 用于教学演示什么是Skill以及如何使用。
 */
@RestController
@RequestMapping("/api/skills")
@CrossOrigin(origins = "*")
public class SkillController {

    @Autowired
    private SkillExecutor skillExecutor;

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private RuleEngine ruleEngine;

    /**
     * 获取所有技能列表
     * @return 技能列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> getAllSkills() {
        List<Skill> skills = skillExecutor.getAllSkills();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Skill skill : skills) {
            Map<String, Object> skillInfo = new HashMap<>();
            skillInfo.put("name", skill.getName());
            skillInfo.put("description", skill.getDescription());
            skillInfo.put("category", skill.getCategory());
            skillInfo.put("requiredParameters", skill.getRequiredParameters());
            result.add(skillInfo);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有技能类别
     * @return 类别列表
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(skillExecutor.getSkillCategories());
    }

    /**
     * 根据类别获取技能
     * @param category 技能类别
     * @return 技能列表
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<Map<String, Object>>> getSkillsByCategory(@PathVariable String category) {
        List<Skill> skills = skillExecutor.getSkillsByCategory(category);
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Skill skill : skills) {
            Map<String, Object> skillInfo = new HashMap<>();
            skillInfo.put("name", skill.getName());
            skillInfo.put("description", skill.getDescription());
            skillInfo.put("category", skill.getCategory());
            result.add(skillInfo);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取技能详情
     * @param skillName 技能名称
     * @return 技能详情
     */
    @GetMapping("/{skillName}")
    public ResponseEntity<Map<String, Object>> getSkillInfo(@PathVariable String skillName) {
        SkillMetadata metadata = skillExecutor.getSkillInfo(skillName);
        
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("name", metadata.getSkillName());
        result.put("description", metadata.getDescription());
        result.put("category", metadata.getCategory());
        result.put("status", metadata.getStatus());
        result.put("registeredAt", metadata.getRegisteredAt());
        result.put("executionCount", metadata.getExecutionCount());
        result.put("averageExecutionTime", metadata.getAverageExecutionTime() + "ms");
        result.put("requiredParameters", metadata.getRequiredParameters());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 执行技能
     * @param skillName 技能名称
     * @param request 请求体，包含 input 和 context
     * @return 执行结果
     */
    @PostMapping("/{skillName}/execute")
    public ResponseEntity<Map<String, Object>> executeSkill(
            @PathVariable String skillName,
            @RequestBody Map<String, Object> request) {
        
        String input = (String) request.get("input");
        Map<String, Object> context = (Map<String, Object>) request.get("context");
        
        if (context == null) {
            context = new HashMap<>();
        }
        
        com.bookstore.agent.skill.SkillResult result = skillExecutor.executeSkill(skillName, input, context);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("data", result.getData());
        response.put("errorMessage", result.getErrorMessage());
        response.put("context", result.getContext());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 根据规则执行技能
     * @param skillName 技能名称
     * @param request 请求体，包含 input、context 和 requiredRules
     * @return 执行结果
     */
    @PostMapping("/{skillName}/execute-with-rules")
    public ResponseEntity<Map<String, Object>> executeSkillWithRules(
            @PathVariable String skillName,
            @RequestBody Map<String, Object> request) {
        
        String input = (String) request.get("input");
        Map<String, Object> context = (Map<String, Object>) request.get("context");
        List<String> requiredRules = (List<String>) request.get("requiredRules");
        
        if (context == null) {
            context = new HashMap<>();
        }
        
        com.bookstore.agent.skill.SkillResult result = 
                skillExecutor.executeSkillWithRules(skillName, input, context, requiredRules);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("data", result.getData());
        response.put("errorMessage", result.getErrorMessage());
        response.put("context", result.getContext());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有规则列表
     * @return 规则列表
     */
    @GetMapping("/rules/list")
    public ResponseEntity<List<Map<String, Object>>> getAllRules() {
        List<Rule> rules = ruleEngine.getAllRules();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Rule rule : rules) {
            Map<String, Object> ruleInfo = new HashMap<>();
            ruleInfo.put("name", rule.getName());
            ruleInfo.put("description", rule.getDescription());
            ruleInfo.put("priority", rule.getPriority());
            result.add(ruleInfo);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 评估规则
     * @param ruleName 规则名称
     * @param context 上下文信息
     * @return 评估结果
     */
    @PostMapping("/rules/{ruleName}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateRule(
            @PathVariable String ruleName,
            @RequestBody Map<String, Object> context) {
        
        boolean result = ruleEngine.evaluateRule(ruleName, context);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ruleName", ruleName);
        response.put("result", result);
        response.put("context", context);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 评估规则集（AND逻辑）
     * @param ruleSetName 规则集名称
     * @param context 上下文信息
     * @return 评估结果
     */
    @PostMapping("/ruleset/{ruleSetName}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateRuleSet(
            @PathVariable String ruleSetName,
            @RequestBody Map<String, Object> context) {
        
        boolean result = ruleEngine.evaluateRuleSet(ruleSetName, context);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ruleSetName", ruleSetName);
        response.put("result", result);
        response.put("context", context);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取技能执行统计
     * @return 统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalSkills", skillRegistry.getSkillCount());
        stats.put("totalRules", ruleEngine.getRuleCount());
        stats.put("categories", skillRegistry.getAllCategories());
        
        // 获取每个技能的执行统计
        List<Map<String, Object>> skillStats = new ArrayList<>();
        for (String skillName : skillRegistry.getAllSkillNames()) {
            SkillMetadata metadata = skillRegistry.getSkillMetadata(skillName);
            if (metadata != null) {
                Map<String, Object> stat = new HashMap<>();
                stat.put("name", skillName);
                stat.put("executionCount", metadata.getExecutionCount());
                stat.put("averageExecutionTime", metadata.getAverageExecutionTime() + "ms");
                stat.put("status", metadata.getStatus());
                skillStats.add(stat);
            }
        }
        stats.put("skillStatistics", skillStats);
        
        return ResponseEntity.ok(stats);
    }

    /**
     * 创建技能执行上下文
     * @param userId 用户ID（可选）
     * @return 上下文对象
     */
    @GetMapping("/context/create")
    public ResponseEntity<Map<String, Object>> createContext(
            @RequestParam(required = false) Long userId) {
        Map<String, Object> context = skillExecutor.createContext(userId);
        return ResponseEntity.ok(context);
    }
}