package com.bookstore.service;

import com.bookstore.dto.LoginRequestDTO;
import com.bookstore.dto.RegisterRequestDTO;
import com.bookstore.dto.UserBanRequestDTO;
import com.bookstore.entity.User;
import com.bookstore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 用户服务类
 * 实现用户相关的业务逻辑
 */
@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    
    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, 
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }
    
    /**
     * 用户注册
     * @param registerRequestDTO 注册请求DTO
     * @return 注册成功的用户
     * @throws Exception 注册失败异常
     */
    @Transactional
    public User register(RegisterRequestDTO registerRequestDTO) throws Exception {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(registerRequestDTO.getUsername())) {
            throw new Exception("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (userRepository.existsByEmail(registerRequestDTO.getEmail())) {
            throw new Exception("邮箱已存在");
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(registerRequestDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequestDTO.getPassword()));
        user.setEmail(registerRequestDTO.getEmail());
        user.setRole(User.Role.CUSTOMER); // 默认角色为顾客
        user.setStatus(User.Status.ACTIVE); // 默认状态为活跃
        
        // 保存用户
        return userRepository.save(user);
    }
    
    /**
     * 用户登录
     * @param loginRequestDTO 登录请求DTO
     * @return 登录成功的用户
     * @throws Exception 登录失败异常
     */
    public User login(LoginRequestDTO loginRequestDTO) throws Exception {
        try {
            System.out.println("登录请求: 用户名=" + loginRequestDTO.getUsername() + ", 密码长度=" + (loginRequestDTO.getPassword() != null ? loginRequestDTO.getPassword().length() : 0));
            
            // 检查用户是否存在
            User user = userRepository.findByUsername(loginRequestDTO.getUsername())
                .orElseThrow(() -> new Exception("用户不存在"));
            
            System.out.println("找到用户: " + user.getUsername() + ", 密码哈希=" + user.getPassword());
            
            // 进行认证
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequestDTO.getUsername(),
                    loginRequestDTO.getPassword()
                )
            );
            
            System.out.println("认证成功: " + authentication.isAuthenticated());
            
            // 设置认证信息到上下文
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // 获取用户信息
            return user;
        } catch (Exception e) {
            // 处理认证异常
            System.out.println("登录失败: " + e.getMessage());
            if (e.getMessage().contains("您的账号已被管理员禁用")) {
                throw e;
            }
            throw new Exception("用户名或密码错误");
        }
    }
    
    /**
     * 禁用/解禁用户
     * @param userBanRequestDTO 用户封禁请求DTO
     * @param currentUserId 当前操作的管理员ID
     * @return 操作后的用户
     * @throws Exception 操作失败异常
     */
    @Transactional
    public User banUser(UserBanRequestDTO userBanRequestDTO, Long currentUserId) throws Exception {
        // 检查当前用户是否是管理员
        User currentUser = userRepository.findById(currentUserId)
            .orElseThrow(() -> new Exception("当前用户不存在"));
        
        if (currentUser.getRole() != User.Role.ADMIN) {
            throw new Exception("只有管理员可以执行此操作");
        }
        
        // 查找要操作的用户
        User user = userRepository.findById(userBanRequestDTO.getUserId())
            .orElseThrow(() -> new Exception("要操作的用户不存在"));
        
        // 管理员不能禁用自己
        if (user.getId().equals(currentUserId)) {
            throw new Exception("管理员不能禁用自己");
        }
        
        // 管理员不能被禁用
        if (user.getRole() == User.Role.ADMIN) {
            throw new Exception("管理员账号不能被禁用");
        }
        
        // 更新用户状态
        user.setStatus(userBanRequestDTO.getBanned() ? User.Status.BANNED : User.Status.ACTIVE);
        
        // 保存用户
        return userRepository.save(user);
    }
    
    /**
     * 根据ID获取用户
     * @param userId 用户ID
     * @return 用户对象
     * @throws Exception 用户不存在异常
     */
    public User getUserById(Long userId) throws Exception {
        return userRepository.findById(userId)
            .orElseThrow(() -> new Exception("用户不存在"));
    }
    
    /**
     * 根据用户名获取用户
     * @param username 用户名
     * @return 用户对象
     * @throws Exception 用户不存在异常
     */
    public User getUserByUsername(String username) throws Exception {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new Exception("用户不存在"));
    }
    
    /**
     * 检查用户是否被禁用
     * @param username 用户名
     * @return 是否被禁用
     */
    public boolean isUserBanned(String username) {
        Optional<User> user = userRepository.findByUsername(username);
        return user.isPresent() && user.get().getStatus() == User.Status.BANNED;
    }
}