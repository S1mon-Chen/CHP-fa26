package com.bookstore.tx.common;

import org.apache.seata.core.context.RootContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.bookstore.tx.common.Contracts.*;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> business(BusinessException e) {
        log.warn("REQUEST_FAILED code={} requestId={} xid={} message={}", e.code(), e.requestId(), e.xid(), e.getMessage());
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), e.xid(), e.requestId()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalid(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "请求字段非法，请检查 requestId、bookId 和 quantity", RootContext.getXID(), null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("UNEXPECTED_FAILURE xid={}", RootContext.getXID(), e);
        return ResponseEntity.internalServerError().body(new ApiError("INTERNAL_ERROR", "服务异常，请凭 requestId 查询结果", RootContext.getXID(), null));
    }
}
