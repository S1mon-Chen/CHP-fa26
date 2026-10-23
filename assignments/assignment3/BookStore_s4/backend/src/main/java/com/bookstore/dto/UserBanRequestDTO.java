package com.bookstore.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 用户封禁请求数据传输对象
 * 用于接收管理员封禁/解封用户的请求参数
 */
public class UserBanRequestDTO {
    
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
    @NotNull(message = "封禁状态不能为空")
    private Boolean banned;
    
    // 无参构造函数
    public UserBanRequestDTO() {
    }
    
    // 全参构造函数
    public UserBanRequestDTO(Long userId, Boolean banned) {
        this.userId = userId;
        this.banned = banned;
    }
    
    // Getter 和 Setter 方法
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public Boolean getBanned() {
        return banned;
    }
    
    public void setBanned(Boolean banned) {
        this.banned = banned;
    }
    
    @Override
    public String toString() {
        return "UserBanRequestDTO{" +
                "userId=" + userId +
                ", banned=" + banned +
                '}';
    }
}