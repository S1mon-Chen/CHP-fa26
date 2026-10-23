package com.bookstore.controller;

import com.bookstore.dto.LoginRequestDTO;
import com.bookstore.dto.RegisterRequestDTO;
import com.bookstore.dto.UserBanRequestDTO;
import com.bookstore.entity.User;
import com.bookstore.dto.UserResponseDTO;
import com.bookstore.service.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
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
    private final SecurityContextRepository securityContexts;
    private final SessionAuthenticationStrategy sessions;
    
    @Autowired
    public UserController(UserService userService, CurrentUser currentUser,
                          SecurityContextRepository securityContexts, SessionAuthenticationStrategy sessions) {
        this.userService = userService;
        this.currentUser = currentUser;
        this.securityContexts = securityContexts;
        this.sessions = sessions;
    }

    @GetMapping("/auth/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }
    
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
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO,
                                   HttpServletRequest request, HttpServletResponse servletResponse) {
        try {
            User user = userService.login(loginRequestDTO);
            var context = SecurityContextHolder.getContext();
            sessions.onAuthentication(context.getAuthentication(), request, servletResponse);
            securityContexts.saveContext(context, request, servletResponse);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "登录成功");
            response.put("user", UserResponseDTO.from(user));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }
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
