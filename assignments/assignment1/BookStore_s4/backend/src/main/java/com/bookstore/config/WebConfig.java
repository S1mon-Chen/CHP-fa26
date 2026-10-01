package com.bookstore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * 配置Web相关设置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // CORS配置已移至 SecurityConfig
}