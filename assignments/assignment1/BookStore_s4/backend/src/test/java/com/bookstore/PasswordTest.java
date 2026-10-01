package com.bookstore;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 测试密码
        String password = "password";
        String encodedPassword = "$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW";
        
        System.out.println("原始密码: " + password);
        System.out.println("数据库中的密码哈希: " + encodedPassword);
        System.out.println("密码匹配: " + encoder.matches(password, encodedPassword));
        
        // 生成新的密码哈希
        String newEncodedPassword = encoder.encode(password);
        System.out.println("新生成的密码哈希: " + newEncodedPassword);
        System.out.println("新哈希与原始密码匹配: " + encoder.matches(password, newEncodedPassword));
    }
}