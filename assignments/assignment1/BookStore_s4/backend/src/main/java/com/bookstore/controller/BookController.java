package com.bookstore.controller;

import com.bookstore.service.BookService;
import com.bookstore.utils.BookCoverGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 书籍控制器
 * 处理书籍相关的 HTTP 请求
 */
@RestController
@RequestMapping("/api/books")
public class BookController {
    
    private final BookService bookService;
    private final BookCoverGenerator bookCoverGenerator;
    
    @Autowired
    public BookController(BookService bookService, BookCoverGenerator bookCoverGenerator) {
        this.bookService = bookService;
        this.bookCoverGenerator = bookCoverGenerator;
    }
    
    /**
     * 获取所有书籍
     * @return 书籍列表
     */
    @GetMapping
    public ResponseEntity<?> getAllBooks() {
        try {
            return ResponseEntity.ok(bookService.getAllBooks());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    /**
     * 根据ID获取书籍
     * @param id 书籍ID
     * @return 书籍详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getBookById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bookService.getBookById(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    /**
     * 搜索书籍
     * @param keyword 搜索关键词
     * @return 搜索结果
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchBooks(@RequestParam String keyword) {
        try {
            return ResponseEntity.ok(bookService.searchBooks(keyword));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    /**
     * 为所有书籍生成封面
     * @return 操作结果
     */
    @PostMapping("/generate-covers")
    public ResponseEntity<?> generateCovers() {
        try {
            bookCoverGenerator.generateCoversForAllBooks();
            return ResponseEntity.ok(Map.of("message", "封面生成成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}