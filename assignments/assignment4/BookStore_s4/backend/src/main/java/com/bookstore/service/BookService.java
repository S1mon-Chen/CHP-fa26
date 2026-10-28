package com.bookstore.service;

import com.bookstore.dto.BookResponseDTO;
import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 书籍服务类
 * 实现书籍相关的业务逻辑
 */
@Service
public class BookService {
    
    private final BookRepository bookRepository;
    private final long delayMs;
    
    @Autowired
    public BookService(BookRepository bookRepository, @Value("${app.books.delay-ms:200}") long delayMs) {
        this.bookRepository = bookRepository;
        if (delayMs < 0 || delayMs > 2000) throw new IllegalArgumentException("Invalid simulated I/O delay");
        this.delayMs = delayMs;
    }
    
    /**
     * 获取所有书籍
     * @return 书籍列表
     */
    public List<BookResponseDTO> getAllBooks() {
        simulateDatabaseDelay();
        List<Book> books = bookRepository.findAll();
        return books.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据ID获取书籍
     * @param id 书籍ID
     * @return 书籍DTO
     * @throws Exception 书籍不存在异常
     */
    public BookResponseDTO getBookById(Long id) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图书 ID 必须为正整数");
        simulateDatabaseDelay();
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "书籍不存在: " + id));
        return convertToDTO(book);
    }
    
    /**
     * 搜索书籍
     * @param keyword 搜索关键词
     * @return 书籍列表
     */
    public List<BookResponseDTO> searchBooks(String keyword) {
        simulateDatabaseDelay();
        List<Book> books = bookRepository.findByTitleContainingOrAuthorContaining(keyword, keyword);
        return books.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 将 Book 实体转换为 BookResponseDTO
     * @param book 书籍实体
     * @return 书籍DTO
     */
    private BookResponseDTO convertToDTO(Book book) {
        return new BookResponseDTO(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getDescription(),
                book.getPrice(),
                book.getStock(),
                book.getCoverImage(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
    /** Simulate I/O on the actual Web request thread before accessing the real repository. */
    private void simulateDatabaseDelay() {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "查询被中断");
        }
    }

}
