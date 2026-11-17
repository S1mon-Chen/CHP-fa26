package com.bookstore.cache;

import java.util.Map;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalid(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST"));
    }
    @ExceptionHandler(BookCacheService.CacheInvalidationException.class)
    public ResponseEntity<?> committed(Exception error) {
        return ResponseEntity.status(503).body(Map.of("code", "CACHE_INVALIDATION_FAILED",
                "databaseCommitted", true, "message", "数据库已提交，缓存删除失败，旧缓存将按 TTL 到期"));
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<?> unavailable(Exception error) {
        // Do not bypass Redis and flood MySQL when Redis is unavailable.
        return ResponseEntity.status(503).body(Map.of("code", "DEPENDENCY_UNAVAILABLE"));
    }
}
