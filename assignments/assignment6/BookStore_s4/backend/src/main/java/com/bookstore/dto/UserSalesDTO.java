package com.bookstore.dto;

import java.math.BigDecimal;

/**
 * 用户消费统计DTO
 * 用于存储用户消费统计数据
 */
public class UserSalesDTO {
    private Long userId;
    private String username;
    private int totalOrders;
    private BigDecimal totalAmount;

    // 构造函数
    public UserSalesDTO(Long userId, String username, int totalOrders, BigDecimal totalAmount) {
        this.userId = userId;
        this.username = username;
        this.totalOrders = totalOrders;
        this.totalAmount = totalAmount;
    }

    // Getter 和 Setter 方法
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}