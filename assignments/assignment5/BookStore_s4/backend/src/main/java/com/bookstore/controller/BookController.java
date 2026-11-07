package com.bookstore.controller;

import com.bookstore.service.BookService;
import com.bookstore.dto.BookResponseDTO;
import java.util.List;
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
    public ResponseEntity<List<BookResponseDTO>> getAllBooks() {
        return queryResponse(bookService.getAllBooks());
    }
    
    /**
     * 根据ID获取书籍
     * @param id 书籍ID
     * @return 书籍详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<BookResponseDTO> getBookById(@PathVariable Long id) {
        return queryResponse(bookService.getBookById(id));
    }
    
    /**
     * 搜索书籍
     * @param keyword 搜索关键词
     * @return 搜索结果
     */
    @GetMapping("/search")
    public ResponseEntity<List<BookResponseDTO>> searchBooks(@RequestParam String keyword) {
        return queryResponse(bookService.searchBooks(keyword));
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
    private <T> ResponseEntity<T> queryResponse(T body) {
        Thread thread = Thread.currentThread();
        return ResponseEntity.ok().header("X-Request-Virtual", Boolean.toString(thread.isVirtual()))
                .header("X-Request-Thread", thread.getName()).body(body);
    }

}