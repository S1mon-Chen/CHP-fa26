package com.bookstore.agent.rule.impl;

import com.bookstore.agent.rule.Rule;
import com.bookstore.entity.User;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户是管理员规则
 * 
 * 判断当前用户是否为管理员。
 */
@Component
public class IsAdminRule implements Rule {

    @Override
    public String getName() {
        return "IsAdminRule";
    }

    @Override
    public String getDescription() {
        return "判断当前用户是否为管理员";
    }

    @Override
    public boolean evaluate(Map<String, Object> context) {
        if (context == null) {
            return false;
        }

        // 方式1：检查上下文中的 user 对象
        Object userObj = context.get("user");
        if (userObj instanceof User) {
            User user = (User) userObj;
            return user.getRole() == User.Role.ADMIN;
        }

        // 方式2：检查上下文中的 role 字段
        Object roleObj = context.get("role");
        if (roleObj instanceof String) {
            String role = (String) roleObj;
            return "ADMIN".equals(role) || "管理员".equals(role);
        }

        return false;
    }

    @Override
    public int getPriority() {
        return 15;
    }
}