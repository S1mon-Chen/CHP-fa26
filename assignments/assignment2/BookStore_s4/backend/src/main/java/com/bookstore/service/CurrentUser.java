package com.bookstore.service;

import com.bookstore.entity.User;
import com.bookstore.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves identity from the authenticated session, never from client IDs or roles. */
@Component
public class CurrentUser {
    private final UserRepository users;

    public CurrentUser(UserRepository users) { this.users = users; }

    public User optional() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return null;
        User user = users.findByUsername(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("请重新登录"));
        if (user.getStatus() != User.Status.ACTIVE) throw new AccessDeniedException("账号已被禁用");
        return user;
    }

    public User require() {
        User user = optional();
        if (user == null) throw new AuthenticationCredentialsNotFoundException("请先登录");
        return user;
    }

    public void requireOwnerOrAdmin(Long ownerId) {
        User user = require();
        if (!user.getId().equals(ownerId) && user.getRole() != User.Role.ADMIN)
            throw new AccessDeniedException("无权访问该用户的订单");
    }

    public void requireAdmin() {
        if (require().getRole() != User.Role.ADMIN) throw new AccessDeniedException("需要管理员权限");
    }
}
