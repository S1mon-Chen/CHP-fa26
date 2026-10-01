package com.bookstore.dto;

import java.math.BigDecimal;

/**
 * 书籍销量统计DTO
 */
public class BookSalesDTO {
    private Long bookId;
    private String bookTitle;
    private String bookAuthor;
    private Integer totalQuantity;
    private BigDecimal totalAmount;
    
    public BookSalesDTO() {
    }
    
    public BookSalesDTO(Long bookId, String bookTitle, String bookAuthor, 
                        Integer totalQuantity, BigDecimal totalAmount) {
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.bookAuthor = bookAuthor;
        this.totalQuantity = totalQuantity;
        this.totalAmount = totalAmount;
    }
    
    public Long getBookId() {
        return bookId;
    }
    
    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }
    
    public String getBookTitle() {
        return bookTitle;
    }
    
    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }
    
    public String getBookAuthor() {
        return bookAuthor;
    }
    
    public void setBookAuthor(String bookAuthor) {
        this.bookAuthor = bookAuthor;
    }
    
    public Integer getTotalQuantity() {
        return totalQuantity;
    }
    
    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    @Override
    public String toString() {
        return "BookSalesDTO{" +
                "bookId=" + bookId +
                ", bookTitle='" + bookTitle + '\'' +
                ", bookAuthor='" + bookAuthor + '\'' +
                ", totalQuantity=" + totalQuantity +
                ", totalAmount=" + totalAmount +
                '}';
    }
}
