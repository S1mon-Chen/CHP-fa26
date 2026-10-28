package com.bookstore.assignment4;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    public static void unauthorized(ObjectMapper mapper, HttpServletRequest request,
                                    HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(response.getOutputStream(), Map.of(
                "status", 401, "error", "Unauthorized",
                "message", "需要有效的 Bearer Token", "path", request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException error) {
        int status = error.getStatusCode().value();
        return ResponseEntity.status(status).body(Map.of(
                "status", status, "error", HttpStatus.valueOf(status).getReasonPhrase(),
                "message", error.getReason() == null ? "请求失败" : error.getReason()));
    }
}
