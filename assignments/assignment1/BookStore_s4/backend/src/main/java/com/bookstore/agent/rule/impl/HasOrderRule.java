package com.bookstore.agent.rule.impl;

import com.bookstore.agent.rule.Rule;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 用户有订单规则
 * 
 * 判断用户是否有订单（上下文中包含orders列表且不为空）。
 */
@Component
public class HasOrderRule implements Rule {

    @Override
    public String getName() {
        return "HasOrderRule";
    }

    @Override
    public String getDescription() {
        return "判断用户是否有订单（上下文中包含orders列表且不为空）";
    }

    @Override
    public boolean evaluate(Map<String, Object> context) {
        if (context == null) {
            return false;
        }

        // 方式1：检查 orders 列表
        Object ordersObj = context.get("orders");
        if (ordersObj instanceof List) {
            List<?> orders = (List<?>) ordersObj;
            return !orders.isEmpty();
        }

        // 方式2：检查 hasOrders 布尔值
        Object hasOrdersObj = context.get("hasOrders");
        if (hasOrdersObj instanceof Boolean) {
            return (Boolean) hasOrdersObj;
        }

        return false;
    }

    @Override
    public int getPriority() {
        return 20;
    }
}