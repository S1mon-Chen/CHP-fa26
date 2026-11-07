package com.bookstore.repository;

import com.bookstore.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单项数据访问层
 * 提供订单项相关的数据库操作
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    
    /**
     * 根据订单ID查找订单项
     * @param orderId 订单ID
     * @return 订单项列表
     */
    List<OrderItem> findByOrderId(Long orderId);
    
    /**
     * 根据书籍ID查找订单项
     * @param bookId 书籍ID
     * @return 订单项列表
     */
    List<OrderItem> findByBookId(Long bookId);
    
    /**
     * 根据订单ID和书籍ID查找订单项
     * @param orderId 订单ID
     * @param bookId 书籍ID
     * @return 订单项
     */
    OrderItem findByOrderIdAndBookId(Long orderId, Long bookId);
}