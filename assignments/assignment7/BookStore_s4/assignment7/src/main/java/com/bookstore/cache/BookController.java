package com.bookstore.cache;

import java.util.Map;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/books")
public class BookController {
    private final BookCacheService books;
    private final BookFlowControl limiter;
    private final Metrics metrics;
    public BookController(BookCacheService books, BookFlowControl limiter, Metrics metrics) {
        this.books = books;
        this.limiter = limiter;
        this.metrics = metrics;
    }
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable @Positive long id) {
        try (Entry entry = limiter.enter()) {
            metrics.admitted.increment();
            BookCacheService.Lookup result = books.find(id);
            if (result.book() == null) {
                return ResponseEntity.status(404).header("X-Cache", result.source())
                        .body(Map.of("code", "BOOK_NOT_FOUND", "bookId", id));
            }
            return ResponseEntity.ok().header("X-Cache", result.source()).body(result.book());
        } catch (BlockException blocked) {
            metrics.blocked.increment();
            return ResponseEntity.status(429).header("Retry-After", "1")
                    .header("X-RateLimit-Resource", BookFlowControl.RESOURCE)
                    .body(Map.of("code", "TOO_MANY_REQUESTS", "message", "请求过于频繁，请稍后重试",
                            "resource", BookFlowControl.RESOURCE));
        }
    }

    @PutMapping("/{id}")
    public Book save(@PathVariable @Positive long id, @Valid @RequestBody Book.Write body) {
        return books.save(id, body);
    }
}
