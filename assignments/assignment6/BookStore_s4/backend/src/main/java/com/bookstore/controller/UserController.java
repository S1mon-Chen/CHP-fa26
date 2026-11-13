package com.bookstore.controller;

import com.bookstore.dto.LoginRequestDTO;
import com.bookstore.dto.LoginResponseDTO;
import com.bookstore.security.JwtService;
import com.bookstore.dto.RegisterRequestDTO;
import com.bookstore.dto.UserBanRequestDTO;
import com.bookstore.entity.User;
import com.bookstore.dto.UserResponseDTO;
import com.bookstore.service.CurrentUser;
import com.bookstore.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 * 处理用户相关的 HTTP 请求
 */
@RestController
@RequestMapping("/api")
public class UserController {
    
    private final UserService userService;
    private final CurrentUser currentUser;
    private final JwtService jwt;
    
    @Autowired
    public UserController(UserService userService, CurrentUser currentUser,
                          JwtService jwt) {
        this.userService = userService;
        this.currentUser = currentUser;
        this.jwt = jwt;
    }

    /** Stateless logout: the client discards its token; no session is created or invalidated. */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 用户注册
     * @param registerRequestDTO 注册请求DTO
     * @return 注册结果
     */
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO registerRequestDTO) {
        try {
            User user = userService.register(registerRequestDTO);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "注册成功");
            response.put("user", UserResponseDTO.from(user));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 用户登录
     * @param loginRequestDTO 登录请求DTO
     * @return 登录结果
     */
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
        User user = userService.login(loginRequestDTO);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(new LoginResponseDTO(
                "登录成功", UserResponseDTO.from(user), jwt.issue(user.getUsername()), "Bearer", jwt.ttlSeconds()));
    }

    /**
     * 禁用/解禁用户
     * @param userBanRequestDTO 用户封禁请求DTO
     * @return 操作结果
     */
    @PostMapping("/admin/ban-user")
    public ResponseEntity<?> banUser(@Valid @RequestBody UserBanRequestDTO userBanRequestDTO) {
        try {
            // 获取当前登录用户ID
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String currentUsername = authentication.getName();
            User currentUser = userService.getUserByUsername(currentUsername);
            
            User user = userService.banUser(userBanRequestDTO, currentUser.getId());
            Map<String, Object> response = new HashMap<>();
            response.put("message", userBanRequestDTO.getBanned() ? "用户已禁用" : "用户已解禁");
            response.put("user", UserResponseDTO.from(user));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 获取当前登录用户信息
     * @return 当前用户信息
     */
    @GetMapping("/user/me")
    public ResponseEntity<?> getCurrentUser() {
        return ResponseEntity.ok(UserResponseDTO.from(currentUser.require()));
    }
    
    /**
     * 根据ID获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    @GetMapping("/admin/users/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        try {
            User user = userService.getUserById(userId);
            return ResponseEntity.ok(UserResponseDTO.from(user));
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
}
