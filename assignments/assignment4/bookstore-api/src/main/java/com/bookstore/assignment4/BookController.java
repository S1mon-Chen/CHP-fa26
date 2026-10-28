package com.bookstore.assignment4;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {
    public record Book(long id, String title, String author, BigDecimal price, int stock) { }

    private final List<Book> books = List.of(
            new Book(1, "深入理解 Java 虚拟机", "周志明", new BigDecimal("129.00"), 24),
            new Book(2, "微服务设计", "Sam Newman", new BigDecimal("99.00"), 18),
            new Book(3, "设计数据密集型应用", "Martin Kleppmann", new BigDecimal("128.00"), 12));
    private final long delayMs;

    public BookController(@Value("${app.books.delay-ms}") long delayMs) {
        if (delayMs < 0 || delayMs > 2000) {
            throw new IllegalArgumentException("Simulated I/O delay must be between 0 and 2000 ms");
        }
        this.delayMs = delayMs;
    }

    @GetMapping
    public ResponseEntity<List<Book>> list() {
        simulateDatabase();
        return response(books);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Book> get(@PathVariable long id) {
        if (id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图书 ID 必须为正整数");
        }
        simulateDatabase();
        Book book = books.stream().filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图书不存在"));
        return response(book);
    }

    private void simulateDatabase() {
        try {
            // Blocking I/O is simulated on the actual Tomcat request thread.
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "查询被中断");
        }
    }

    private <T> ResponseEntity<T> response(T body) {
        Thread thread = Thread.currentThread();
        return ResponseEntity.ok()
                .header("X-Request-Virtual", Boolean.toString(thread.isVirtual()))
                .header("X-Request-Thread", thread.getName())
                .body(body);
    }
}
