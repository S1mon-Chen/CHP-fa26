package com.bookstore.cache;

import java.util.Map;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "demo.enabled", havingValue = "true")
public class DemoController {
    private final Metrics metrics;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    public DemoController(Metrics metrics, JdbcTemplate jdbc, StringRedisTemplate redis) {
        this.metrics = metrics;
        this.jdbc = jdbc;
        this.redis = redis;
    }
    @GetMapping("/demo/stats")
    public Map<String, Object> stats() {
        return Map.of("metrics", metrics.snapshot(), "rules", FlowRuleManager.getRules());
    }
    @GetMapping("/demo/health")
    public Map<String, Object> health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        String pong = redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());
        return Map.of("mysql", "UP", "redis", pong);
    }
}
