package com.bookstore.agent;

import com.bookstore.entity.User;
import com.bookstore.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务智能体
 * 负责管理用户账户、积分、会员权益等功能
 */
@Component
public class UserServiceAgent extends AbstractAgent {

    @Autowired
    private UserService userService;

    /**
     * 判断是否能处理该意图
     * @param intent 用户意图字符串
     * @return 如果能处理返回 true，否则返回 false
     */
    @Override
    public boolean canHandle(String intent) {
        if (intent == null) {
            return false;
        }
        String intentLower = intent.toLowerCase();
        return intentLower.contains("我的") || 
               intentLower.contains("账户") || 
               intentLower.contains("积分") || 
               intentLower.contains("会员") ||
               intentLower.contains("个人信息") ||
               intentLower.contains("资料");
    }

    /**
     * 执行智能体逻辑
     * @param input 用户输入
     * @param context 上下文信息
     * @return 智能体执行结果
     */
    @Override
    protected AgentResponse doExecute(String input, Map<String, Object> context) {
        try {
            // 检查用户是否已登录
            Long userId = getUserId(context);
            if (userId == null) {
                return AgentResponse.failure("需要先登录才能查看个人信息，请先登录账户。", context);
            }

            // 获取用户信息
            User user = userService.getUserById(userId);

            String inputLower = input.toLowerCase();

            // 判断操作类型
            if (inputLower.contains("积分") || inputLower.contains("余额")) {
                return handleQueryPoints(user, context);
            } else if (inputLower.contains("会员")) {
                return handleQueryMembership(user, context);
            } else if (inputLower.contains("信息") || inputLower.contains("资料")) {
                return handleQueryProfile(user, context);
            } else {
                // 默认返回用户概览
                return handleQueryOverview(user, context);
            }

        } catch (Exception e) {
            logError("用户服务智能体执行异常: {}", e.getMessage());
            return AgentResponse.failure("用户服务暂时不可用，请稍后再试。", context);
        }
    }

    /**
     * 处理查询用户概览请求
     */
    private AgentResponse handleQueryOverview(User user, Map<String, Object> context) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("👤 我的账户\n\n");
        resultBuilder.append("用户名：").append(user.getUsername()).append("\n");
        resultBuilder.append("邮箱：").append(user.getEmail()).append("\n");
        resultBuilder.append("会员等级：").append(getMembershipName(user.getRole().name())).append("\n");
        resultBuilder.append("可用积分：").append(calculatePoints(user)).append(" 积分\n");
        resultBuilder.append("账户状态：").append(user.getStatus() == User.Status.ACTIVE ? "正常" : "已禁用");

        // 更新上下文
        Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
        newContext.put("membershipLevel", user.getRole().name());
        newContext.put("points", calculatePoints(user));

        return AgentResponse.success(resultBuilder.toString(), newContext);
    }

    /**
     * 处理查询积分请求
     */
    private AgentResponse handleQueryPoints(User user, Map<String, Object> context) {
        int points = calculatePoints(user);

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("🏆 我的积分\n\n");
        resultBuilder.append("当前积分：").append(points).append(" 积分\n\n");
        resultBuilder.append("积分规则：\n");
        resultBuilder.append("   每消费1元获得1积分\n");
        resultBuilder.append("   100积分可抵扣1元\n");
        resultBuilder.append("   积分有效期为2年");

        // 更新上下文
        Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
        newContext.put("points", points);

        return AgentResponse.success(resultBuilder.toString(), newContext);
    }

    /**
     * 处理查询会员信息请求
     */
    private AgentResponse handleQueryMembership(User user, Map<String, Object> context) {
        String role = user.getRole().name();
        String membershipName = getMembershipName(role);
        String benefits = getMembershipBenefits(role);

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("⭐ 会员信息\n\n");
        resultBuilder.append("会员等级：").append(membershipName).append("\n\n");
        resultBuilder.append("专属权益：\n");
        resultBuilder.append(benefits);

        // 更新上下文
        Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
        newContext.put("membershipLevel", role);

        return AgentResponse.success(resultBuilder.toString(), newContext);
    }

    /**
     * 处理查询个人资料请求
     */
    private AgentResponse handleQueryProfile(User user, Map<String, Object> context) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("📋 个人信息\n\n");
        resultBuilder.append("用户名：").append(user.getUsername()).append("\n");
        resultBuilder.append("邮箱：").append(user.getEmail()).append("\n");
        resultBuilder.append("注册时间：").append(user.getCreatedAt() != null ? user.getCreatedAt().toString().split("T")[0] : "未知").append("\n");
        resultBuilder.append("账户状态：").append(user.getStatus() == User.Status.ACTIVE ? "正常" : "已禁用");

        return AgentResponse.success(resultBuilder.toString(), context);
    }

    /**
     * 获取会员等级名称
     */
    private String getMembershipName(String role) {
        return switch (role.toUpperCase()) {
            case "ADMIN" -> "管理员";
            case "DIAMOND" -> "钻石会员";
            case "GOLD" -> "金卡会员";
            case "SILVER" -> "银卡会员";
            default -> "普通会员";
        };
    }

    /**
     * 获取会员权益描述
     */
    private String getMembershipBenefits(String role) {
        return switch (role.toUpperCase()) {
            case "ADMIN" -> "   ✅ 管理后台权限\n   ✅ 查看所有订单\n   ✅ 用户管理功能";
            case "DIAMOND" -> "   ✅ 全场85折优惠\n   ✅ 专属客服\n   ✅ 优先发货\n   ✅ 生日礼包";
            case "GOLD" -> "   ✅ 全场9折优惠\n   ✅ 专属客服\n   ✅ 优先发货";
            case "SILVER" -> "   ✅ 全场95折优惠\n   ✅ 专属客服";
            default -> "   ✅ 基础购物服务\n   ✅ 积分累计";
        };
    }

    /**
     * 计算用户积分（模拟计算）
     */
    private int calculatePoints(User user) {
        // 模拟积分计算：根据用户ID生成随机积分
        // 在实际系统中，积分应该存储在数据库中
        return (int) (user.getId() * 100 + Math.random() * 500);
    }
}