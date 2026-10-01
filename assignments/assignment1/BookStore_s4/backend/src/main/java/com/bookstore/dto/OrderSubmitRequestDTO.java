package com.bookstore.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 订单提交请求数据传输对象
 * 用于接收用户提交订单的请求参数
 */
public class OrderSubmitRequestDTO {
    
    @NotEmpty(message = "订单项不能为空")
    @Valid
    private List<OrderItemDTO> items;
    
    // 无参构造函数
    public OrderSubmitRequestDTO() {
    }
    
    // 全参构造函数
    public OrderSubmitRequestDTO(List<OrderItemDTO> items) {
        this.items = items;
    }
    
    // Getter 和 Setter 方法
    public List<OrderItemDTO> getItems() {
        return items;
    }
    
    public void setItems(List<OrderItemDTO> items) {
        this.items = items;
    }
    
    @Override
    public String toString() {
        return "OrderSubmitRequestDTO{" +
                "items=" + items +
                '}';
    }
    
    /**
     * 订单项数据传输对象
     * 用于表示订单中的单个商品项
     */
    public static class OrderItemDTO {
        
        @NotNull(message = "书籍ID不能为空")
        private Long bookId;
        
        @NotNull(message = "数量不能为空")
        private Integer quantity;
        
        // 无参构造函数
        public OrderItemDTO() {
        }
        
        // 全参构造函数
        public OrderItemDTO(Long bookId, Integer quantity) {
            this.bookId = bookId;
            this.quantity = quantity;
        }
        
        // Getter 和 Setter 方法
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
        
        @Override
        public String toString() {
            return "OrderItemDTO{" +
                    "bookId=" + bookId +
                    ", quantity=" + quantity +
                    '}';
        }
    }
}