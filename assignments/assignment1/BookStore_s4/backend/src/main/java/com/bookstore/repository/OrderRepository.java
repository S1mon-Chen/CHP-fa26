package com.bookstore.repository;

import com.bookstore.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单数据访问层
 * 提供订单相关的数据库操作
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    
    /**
     * 根据用户ID查找订单
     * @param userId 用户ID
     * @return 订单列表
     */
    List<Order> findByUserId(Long userId);
    
    /**
     * 根据用户ID和订单状态查找订单
     * @param userId 用户ID
     * @param status 订单状态
     * @return 订单列表
     */
    List<Order> findByUserIdAndStatus(Long userId, Order.Status status);
    
    /**
     * 根据订单状态查找订单
     * @param status 订单状态
     * @return 订单列表
     */
    List<Order> findByStatus(Order.Status status);
    
    /**
     * 查找用户的所有订单，按创建时间降序排列
     * @param userId 用户ID
     * @return 订单列表
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * 获取所有订单（包含订单项）
     * @return 订单列表
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.book")
    List<Order> findAllWithOrderItems();
    
    /**
     * 根据用户ID获取订单（包含订单项）
     * @param userId 用户ID
     * @return 订单列表
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.book WHERE o.user.id = :userId ORDER BY o.createdAt DESC")
    List<Order> findByUserIdWithOrderItems(@Param("userId") Long userId);
}