package com.bookstore.shoppingdemo;

import com.bookstore.assistant.*;
import com.bookstore.shopping.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Submission-only launcher: imports exactly the authored RAG modules, with no database. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@PropertySource("classpath:application-assistant.properties")
@Import({ShoppingAgent.class, ShoppingTools.class, KnowledgeBase.class, EmbeddingClient.class,
        ShoppingController.class, ModelClient.class, AssistantSettings.class})
@org.springframework.boot.context.properties.EnableConfigurationProperties
@RestController
public class ShoppingDemoApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(ShoppingDemoApplication.class);
        app.setDefaultProperties(Map.of("server.address", "127.0.0.1", "server.port", "8083"));
        app.run(args);
    }

    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).requestCache(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .httpBasic(b -> b.disable()).formLogin(f -> f.disable()).build();
    }

}
