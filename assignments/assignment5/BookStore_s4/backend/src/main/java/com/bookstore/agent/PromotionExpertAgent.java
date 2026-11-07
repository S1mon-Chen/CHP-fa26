package com.bookstore.agent;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 促销专家智能体
 * 负责提供优惠信息、计算折扣、发放优惠券等功能
 */
@Component
public class PromotionExpertAgent extends AbstractAgent {

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
        return intentLower.contains("优惠") || 
               intentLower.contains("折扣") || 
               intentLower.contains("促销") || 
               intentLower.contains("优惠券") ||
               intentLower.contains("满减");
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
            String inputLower = input.toLowerCase();

            // 判断操作类型
            if (inputLower.contains("查询") || inputLower.contains("有什么")) {
                return handleQueryPromotions(context);
            } else if (inputLower.contains("计算") || inputLower.contains("多少钱")) {
                return handleCalculateDiscount(context);
            } else {
                // 默认返回促销信息
                return handleQueryPromotions(context);
            }

        } catch (Exception e) {
            logError("促销专家智能体执行异常: {}", e.getMessage());
            return AgentResponse.failure("促销服务暂时不可用，请稍后再试。", context);
        }
    }

    /**
     * 处理查询促销活动请求
     */
    private AgentResponse handleQueryPromotions(Map<String, Object> context) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("🎁 当前促销活动\n\n");
        resultBuilder.append("1. 全场图书8折优惠\n");
        resultBuilder.append("   活动时间：即日起至2026年5月31日\n\n");
        resultBuilder.append("2. 满减优惠\n");
        resultBuilder.append("   满100元减20元\n");
        resultBuilder.append("   满200元减50元\n");
        resultBuilder.append("   满500元减150元\n\n");
        resultBuilder.append("3. 新用户专享\n");
        resultBuilder.append("   注册即送50元优惠券\n");
        resultBuilder.append("   首单再享额外9折\n\n");
        resultBuilder.append("4. 会员权益\n");
        resultBuilder.append("   银卡会员：95折\n");
        resultBuilder.append("   金卡会员：9折\n");
        resultBuilder.append("   钻石会员：85折");

        return AgentResponse.success(resultBuilder.toString(), context);
    }

    /**
     * 处理计算折扣请求
     */
    private AgentResponse handleCalculateDiscount(Map<String, Object> context) {
        // 从上下文获取订单金额
        Object amountObj = context != null ? context.get("orderAmount") : null;
        BigDecimal originalAmount = BigDecimal.ZERO;

        if (amountObj instanceof BigDecimal) {
            originalAmount = (BigDecimal) amountObj;
        } else if (amountObj instanceof Double) {
            originalAmount = BigDecimal.valueOf((Double) amountObj);
        }

        // 如果没有订单金额，尝试从上下文中获取书籍价格
        if (originalAmount.compareTo(BigDecimal.ZERO) == 0) {
            Map<String, Object> selectedBook = (Map<String, Object>) context.get("selectedBook");
            if (selectedBook != null) {
                Object priceObj = selectedBook.get("price");
                if (priceObj instanceof Double) {
                    originalAmount = BigDecimal.valueOf((Double) priceObj);
                }
            }
        }

        // 获取用户会员等级（默认普通会员）
        String membershipLevel = context != null ? 
            (String) context.getOrDefault("membershipLevel", "NORMAL") : "NORMAL";

        // 计算最优优惠
        CalculationResult result = calculateBestDiscount(originalAmount, membershipLevel);

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("💰 价格计算结果\n\n");
        resultBuilder.append("原价：¥").append(originalAmount.setScale(2, RoundingMode.HALF_UP)).append("\n\n");
        
        if (result.discountAmount.compareTo(BigDecimal.ZERO) > 0) {
            resultBuilder.append("优惠明细：\n");
            resultBuilder.append("   ").append(result.discountType).append("：-¥").append(result.discountAmount.setScale(2, RoundingMode.HALF_UP)).append("\n\n");
            resultBuilder.append("实付金额：¥").append(result.finalAmount.setScale(2, RoundingMode.HALF_UP)).append("\n");
            resultBuilder.append("已为您节省：¥").append(result.discountAmount.setScale(2, RoundingMode.HALF_UP));
        } else {
            resultBuilder.append("当前没有可用的优惠活动\n");
            resultBuilder.append("实付金额：¥").append(originalAmount.setScale(2, RoundingMode.HALF_UP));
        }

        // 更新上下文
        Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
        newContext.put("finalAmount", result.finalAmount);
        newContext.put("discountAmount", result.discountAmount);
        newContext.put("discountType", result.discountType);

        return AgentResponse.success(resultBuilder.toString(), newContext);
    }

    /**
     * 计算最优折扣
     */
    private CalculationResult calculateBestDiscount(BigDecimal originalAmount, String membershipLevel) {
        BigDecimal bestDiscount = BigDecimal.ZERO;
        BigDecimal bestFinalAmount = originalAmount;
        String bestDiscountType = "";

        // 1. 计算会员折扣
        BigDecimal memberDiscount = BigDecimal.ZERO;
        switch (membershipLevel.toUpperCase()) {
            case "SILVER":
                memberDiscount = originalAmount.multiply(BigDecimal.valueOf(0.05));
                break;
            case "GOLD":
                memberDiscount = originalAmount.multiply(BigDecimal.valueOf(0.10));
                break;
            case "DIAMOND":
                memberDiscount = originalAmount.multiply(BigDecimal.valueOf(0.15));
                break;
        }

        // 2. 计算满减优惠
        BigDecimal fullReduction = calculateFullReduction(originalAmount);

        // 3. 计算全场折扣（8折）
        BigDecimal globalDiscount = originalAmount.multiply(BigDecimal.valueOf(0.20));

        // 选择最优优惠
        if (memberDiscount.compareTo(bestDiscount) > 0) {
            bestDiscount = memberDiscount;
            bestFinalAmount = originalAmount.subtract(memberDiscount);
            bestDiscountType = "会员折扣";
        }

        if (fullReduction.compareTo(bestDiscount) > 0) {
            bestDiscount = fullReduction;
            bestFinalAmount = originalAmount.subtract(fullReduction);
            bestDiscountType = "满减优惠";
        }

        if (globalDiscount.compareTo(bestDiscount) > 0) {
            bestDiscount = globalDiscount;
            bestFinalAmount = originalAmount.subtract(globalDiscount);
            bestDiscountType = "全场8折";
        }

        return new CalculationResult(bestFinalAmount, bestDiscount, bestDiscountType);
    }

    /**
     * 计算满减优惠
     */
    private BigDecimal calculateFullReduction(BigDecimal amount) {
        BigDecimal reduction = BigDecimal.ZERO;

        if (amount.compareTo(BigDecimal.valueOf(500)) >= 0) {
            reduction = BigDecimal.valueOf(150);
        } else if (amount.compareTo(BigDecimal.valueOf(200)) >= 0) {
            reduction = BigDecimal.valueOf(50);
        } else if (amount.compareTo(BigDecimal.valueOf(100)) >= 0) {
            reduction = BigDecimal.valueOf(20);
        }

        return reduction;
    }

    /**
     * 计算结果封装类
     */
    private static class CalculationResult {
        BigDecimal finalAmount;
        BigDecimal discountAmount;
        String discountType;

        CalculationResult(BigDecimal finalAmount, BigDecimal discountAmount, String discountType) {
            this.finalAmount = finalAmount;
            this.discountAmount = discountAmount;
            this.discountType = discountType;
        }
    }
}