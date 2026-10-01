package com.bookstore.agent.skill.impl;

import com.bookstore.agent.skill.Skill;
import com.bookstore.agent.skill.SkillResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 促销查询技能
 * 
 * 提供促销活动查询功能，可以查看当前的促销信息。
 */
@Component
public class PromotionQuerySkill implements Skill {

    // 模拟促销活动数据
    private static final List<Promotion> promotions = Arrays.asList(
        new Promotion("新用户专享", "新用户首次下单享受9折优惠", "2024-01-01", "2024-12-31", "NEW_USER"),
        new Promotion("满减活动", "满100减20，满200减50", "2024-06-01", "2024-06-30", "DISCOUNT"),
        new Promotion("会员日", "每月8号会员享受8.5折优惠", "2024-01-01", "2024-12-31", "MEMBER"),
        new Promotion("暑期特惠", "指定书籍7折起", "2024-07-01", "2024-08-31", "SEASONAL")
    );

    @Override
    public String getName() {
        return "PromotionQuerySkill";
    }

    @Override
    public String getDescription() {
        return "查询当前的促销活动和优惠信息";
    }

    @Override
    public String getCategory() {
        return "促销管理";
    }

    @Override
    public boolean canExecute(Map<String, Object> context) {
        // 此技能不需要前置条件
        return true;
    }

    @Override
    public SkillResult execute(String input, Map<String, Object> context) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("当前促销活动：\n\n");

            int index = 1;
            for (Promotion promotion : promotions) {
                sb.append(index++).append(". ").append(promotion.getName()).append("\n")
                  .append("   描述：").append(promotion.getDescription()).append("\n")
                  .append("   时间：").append(promotion.getStartDate()).append(" 至 ").append(promotion.getEndDate()).append("\n\n");
            }

            Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
            newContext.put("promotions", promotions);

            return SkillResult.success(sb.toString(), newContext, "BookSearchSkill");

        } catch (Exception e) {
            return SkillResult.failure("促销查询失败: " + e.getMessage(), context);
        }
    }

    @Override
    public List<String> getRequiredParameters() {
        return Collections.emptyList();
    }

    private static class Promotion {
        private final String name;
        private final String description;
        private final String startDate;
        private final String endDate;
        private final String type;

        public Promotion(String name, String description, String startDate, String endDate, String type) {
            this.name = name;
            this.description = description;
            this.startDate = startDate;
            this.endDate = endDate;
            this.type = type;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getStartDate() { return startDate; }
        public String getEndDate() { return endDate; }
        public String getType() { return type; }
    }
}