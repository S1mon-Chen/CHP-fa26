package com.bookstore.utils;

import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 图书封面生成器
 * 用于生成图书封面并存储到数据库中
 */
@Component
public class BookCoverGenerator {
    
    private final BookRepository bookRepository;
    
    @Autowired
    public BookCoverGenerator(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }
    
    /**
     * 为所有书籍生成封面
     */
    public void generateCoversForAllBooks() {
        List<Book> books = bookRepository.findAll();
        for (Book book : books) {
            try {
                String coverImage = generateCover(book);
                book.setCoverImage(coverImage);
                bookRepository.save(book);
                System.out.println("已为书籍 " + book.getTitle() + " 生成封面");
            } catch (Exception e) {
                System.err.println("生成封面失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 生成图书封面
     * @param book 书籍对象
     * @return 封面图片的Base64编码
     */
    private String generateCover(Book book) {
        // 为每本书生成一个简单的Base64编码的图片
        // 使用不同的颜色和标题首字母
        int bookId = Math.toIntExact(book.getId());
        int colorIndex = bookId % 5;
        
        // 不同的背景颜色
        String[] colors = {
            "#FF6B6B", // 红色
            "#4ECDC4", // 青色
            "#45B7D1", // 蓝色
            "#FFA69E", // 粉色
            "#98D4BB"  // 绿色
        };
        
        String color = colors[colorIndex];
        String initial = book.getTitle().length() > 0 ? book.getTitle().substring(0, 1) : "B";
        
        // 生成一个简单的SVG图片，然后转换为Base64编码
        String svg = "<svg width=\"200\" height=\"300\" xmlns=\"http://www.w3.org/2000/svg\">" +
                "<rect width=\"200\" height=\"300\" fill=\"" + color + "\"/>" +
                "<rect x=\"10\" y=\"10\" width=\"180\" height=\"280\" fill=\"none\" stroke=\"white\" stroke-width=\"2\"/>" +
                "<text x=\"100\" y=\"150\" font-family=\"Arial\" font-size=\"60\" font-weight=\"bold\" text-anchor=\"middle\" fill=\"white\">" + initial + "</text>" +
                "<text x=\"100\" y=\"220\" font-family=\"Arial\" font-size=\"16\" text-anchor=\"middle\" fill=\"white\">" + book.getTitle() + "</text>" +
                "<text x=\"100\" y=\"250\" font-family=\"Arial\" font-size=\"14\" text-anchor=\"middle\" fill=\"white\">" + book.getAuthor() + "</text>" +
                "</svg>";
        
        // 将SVG转换为Base64编码
        return java.util.Base64.getEncoder().encodeToString(svg.getBytes());
    }
}