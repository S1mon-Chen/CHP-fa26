package com.bookstore.agent.skill.impl;

import com.bookstore.agent.skill.Skill;
import com.bookstore.agent.skill.SkillResult;
import com.bookstore.entity.User;
import com.bookstore.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 用户信息技能
 * 
 * 提供用户信息查询功能，可以查看当前登录用户的个人信息。
 */
@Component
public class UserInfoSkill implements Skill {

    @Autowired
    private UserService userService;

    @Override
    public String getName() {
        return "UserInfoSkill";
    }

    @Override
    public String getDescription() {
        return "查询当前登录用户的个人信息，包括用户名、邮箱、积分等";
    }

    @Override
    public String getCategory() {
        return "用户管理";
    }

    @Override
    public boolean canExecute(Map<String, Object> context) {
        // 需要用户已登录
        if (context == null) {
            return false;
        }
        Object userIdObj = context.get("userId");
        return userIdObj != null && (userIdObj instanceof Long || userIdObj instanceof String);
    }

    @Override
    public SkillResult execute(String input, Map<String, Object> context) {
        try {
            Long userId = getUserId(context);
            if (userId == null) {
                return SkillResult.failure("请先登录以查看个人信息", context);
            }

            User user = userService.getUserById(userId);
            if (user == null) {
                return SkillResult.failure("用户不存在", context);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("您的个人信息：\n\n")
              .append("用户名：").append(user.getUsername()).append("\n")
              .append("邮箱：").append(user.getEmail()).append("\n")
              .append("角色：").append(user.getRole() == User.Role.ADMIN ? "管理员" : "普通用户").append("\n")
              .append("状态：").append(user.getStatus() == User.Status.ACTIVE ? "正常" : "已封禁").append("\n")
              .append("注册时间：").append(user.getCreatedAt()).append("\n");

            Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
            newContext.put("user", user);
            newContext.put("username", user.getUsername());
            newContext.put("email", user.getEmail());

            return SkillResult.success(sb.toString(), newContext);

        } catch (Exception e) {
            return SkillResult.failure("获取用户信息失败: " + e.getMessage(), context);
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
}