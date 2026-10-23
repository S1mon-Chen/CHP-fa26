package com.bookstore.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单项实体类
 * 对应数据库 order_items 表
 */
@Entity
@Table(name = "order_items")
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;
    
    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;
    
    @Column(name = "book_id", insertable = false, updatable = false)
    private Long bookId;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    // 无参构造函数
    public OrderItem() {
    }
    
    // 全参构造函数
    public OrderItem(Long id, Order order, Long orderId, Book book, Long bookId, 
                     Integer quantity, BigDecimal price, LocalDateTime createdAt) {
        this.id = id;
        this.order = order;
        this.orderId = orderId;
        this.book = book;
        this.bookId = bookId;
        this.quantity = quantity;
        this.price = price;
        this.createdAt = createdAt;
    }
    
    // Getter 和 Setter 方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Order getOrder() {
        return order;
    }
    
    public void setOrder(Order order) {
        this.order = order;
    }
    
    public Long getOrderId() {
        return orderId;
    }
    
    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    
    public Book getBook() {
        return book;
    }
    
    public void setBook(Book book) {
        this.book = book;
    }
    
    public Long getBookId() {
        return bookId;
    }
    
    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    // 计算小计金额
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "OrderItem{" +
                "id=" + id +
                ", orderId=" + orderId +
                ", bookId=" + bookId +
                ", quantity=" + quantity +
                ", price=" + price +
                ", createdAt=" + createdAt +
                '}';
    }
}