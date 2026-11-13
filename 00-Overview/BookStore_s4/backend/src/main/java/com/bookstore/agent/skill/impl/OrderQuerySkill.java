package com.bookstore.agent.skill.impl;

import com.bookstore.agent.skill.Skill;
import com.bookstore.agent.skill.SkillResult;
import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.entity.Order;
import com.bookstore.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 订单查询技能
 * 
 * 提供订单查询功能，可以查询用户的订单列表。
 * 
 * 使用要求：需要用户已登录（上下文中包含userId）
 */
@Component
public class OrderQuerySkill implements Skill {

    @Autowired
    private OrderService orderService;

    @Override
    public String getName() {
        return "OrderQuerySkill";
    }

    @Override
    public String getDescription() {
        return "查询用户的订单列表，显示订单状态、金额等信息";
    }

    @Override
    public String getCategory() {
        return "订单管理";
    }

    @Override
    public boolean canExecute(Map<String, Object> context) {
        // 需要用户已登录（上下文中包含userId）
        if (context == null) {
            return false;
        }
        Object userIdObj = context.get("userId");
        return userIdObj != null && (userIdObj instanceof Long || userIdObj instanceof String);
    }

    @Override
    public SkillResult execute(String input, Map<String, Object> context) {
        try {
            // 获取用户ID
            Long userId = getUserId(context);
            if (userId == null) {
                return SkillResult.failure("请先登录以查询订单", context);
            }

            // 查询订单
            List<Order> orders = orderService.getUserOrders(userId);

            if (orders.isEmpty()) {
                return SkillResult.success("您还没有任何订单", context);
            }

            // 格式化订单信息
            StringBuilder sb = new StringBuilder();
            sb.append("您共有 ").append(orders.size()).append(" 个订单：\n\n");
            
            int index = 1;
            for (Order order : orders) {
                sb.append(index++).append(". 订单号：").append(order.getId()).append("\n")
                  .append("   状态：").append(getStatusDescription(order.getStatus())).append("\n")
                  .append("   金额：¥").append(order.getTotalAmount()).append("\n")
                  .append("   创建时间：").append(order.getCreatedAt()).append("\n\n");
            }

            Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
            newContext.put("orderCount", orders.size());
            newContext.put("hasOrders", true);

            return SkillResult.success(sb.toString(), newContext);

        } catch (Exception e) {
            return SkillResult.failure("订单查询失败: " + e.getMessage(), context);
        }
    }

    @Override
    public List<String> getRequiredParameters() {
        return Collections.singletonList("userId");
    }

    private Long getUserId(Map<String, Object> context) {
        Object userIdObj = context.get("userId");
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        } else if (userIdObj instanceof String) {
            try {
                return Long.parseLong((String) userIdObj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String getStatusDescription(Order.Status status) {
        switch (status) {
            case PENDING:
                return "待支付";
            case PAID:
                return "已支付";
            case DELIVERED:
                return "已送达";
            case CANCELLED:
                return "已取消";
            default:
                return status.toString();
        }
    }
}