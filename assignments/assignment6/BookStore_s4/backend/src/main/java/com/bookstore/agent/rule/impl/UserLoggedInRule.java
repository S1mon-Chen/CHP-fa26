package com.bookstore.agent.rule.impl;

import com.bookstore.agent.rule.Rule;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户已登录规则
 * 
 * 判断上下文中是否包含有效的用户ID，表示用户已登录。
 */
@Component
public class UserLoggedInRule implements Rule {

    @Override
    public String getName() {
        return "UserLoggedInRule";
    }

    @Override
    public String getDescription() {
        return "判断用户是否已登录（上下文中包含userId）";
    }

    @Override
    public boolean evaluate(Map<String, Object> context) {
        if (context == null) {
            return false;
        }

        Object userIdObj = context.get("userId");
        if (userIdObj == null) {
            return false;
        }

        // 支持 Long 类型和 String 类型的用户ID
        if (userIdObj instanceof Long) {
            return (Long) userIdObj > 0;
        } else if (userIdObj instanceof String) {
            String userIdStr = (String) userIdObj;
            return !userIdStr.isEmpty();
        }

        return false;
    }

    @Override
    public int getPriority() {
        return 10;
    }
}