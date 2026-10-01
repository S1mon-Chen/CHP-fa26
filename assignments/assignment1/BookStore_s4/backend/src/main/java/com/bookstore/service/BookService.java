package com.bookstore.service;

import com.bookstore.dto.BookResponseDTO;
import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 书籍服务类
 * 实现书籍相关的业务逻辑
 */
@Service
public class BookService {
    
    private final BookRepository bookRepository;
    
    @Autowired
    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }
    
    /**
     * 获取所有书籍
     * @return 书籍列表
     */
    public List<BookResponseDTO> getAllBooks() {
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
    public BookResponseDTO getBookById(Long id) throws Exception {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new Exception("书籍不存在: " + id));
        return convertToDTO(book);
    }
    
    /**
     * 搜索书籍
     * @param keyword 搜索关键词
     * @return 书籍列表
     */
    public List<BookResponseDTO> searchBooks(String keyword) {
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
}