package com.bookstore.controller;

import com.bookstore.dto.BookSalesDTO;
import com.bookstore.dto.UserSalesDTO;
import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.entity.Order;
import com.bookstore.service.OrderService;
import com.bookstore.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单控制器
 * 处理订单相关的 HTTP 请求
 */
@RestController
@RequestMapping("/api")
public class OrderController {
    
    private final OrderService orderService;
    private final UserService userService;
    
    @Autowired
    public OrderController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }
    
    /**
     * 提交订单
     * @param orderSubmitRequestDTO 订单提交请求DTO
     * @return 订单创建结果
     */
    @PostMapping("/orders")
    public ResponseEntity<?> submitOrder(@Valid @RequestBody OrderSubmitRequestDTO orderSubmitRequestDTO) {
        try {
            Order order = orderService.submitOrder(orderSubmitRequestDTO, 4L);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "订单创建成功");
            response.put("order", order);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 获取当前用户的订单列表
     * @return 订单列表
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getUserOrders() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            Long userId = userService.getUserByUsername(username).getId();
            
            List<Order> orders = orderService.getUserOrders(userId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 获取订单详情
     * @param orderId 订单ID
     * @return 订单详情
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrderById(@PathVariable Long orderId) {
        try {
            Order order = orderService.getOrderById(orderId);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 取消订单
     * @param orderId 订单ID
     * @return 取消结果
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        try {
            Order order = orderService.cancelOrder(orderId);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "订单已取消");
            response.put("order", order);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 更新订单状态（管理员用）
     * @param orderId 订单ID
     * @param status 新状态
     * @return 更新结果
     */
    @PutMapping("/admin/orders/{orderId}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long orderId, @RequestParam String status) {
        try {
            Order.Status orderStatus = Order.Status.valueOf(status.toUpperCase());
            Order order = orderService.updateOrderStatus(orderId, orderStatus);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "订单状态更新成功");
            response.put("order", order);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 获取所有订单（管理员用）
     * @return 订单列表
     */
    @GetMapping("/admin/orders")
    public ResponseEntity<?> getAllOrders() {
        try {
            List<Order> orders = orderService.getAllOrders();
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 获取所有订单（包含订单项）
     * @return 订单列表
     */
    @GetMapping("/orders/all")
    public ResponseEntity<?> getAllOrdersWithItems() {
        try {
            List<Order> orders = orderService.getAllOrdersWithItems();
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 获取书籍销量统计
     * @return 书籍销量列表
     */
    @GetMapping("/orders/sales-statistics")
    public ResponseEntity<?> getBookSalesStatistics() {
        try {
            List<BookSalesDTO> sales = orderService.getBookSalesStatistics();
            return ResponseEntity.ok(sales);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 根据用户ID获取订单（包含订单项）
     * @param userId 用户ID
     * @return 订单列表
     */
    @GetMapping("/orders/user/{userId}")
    public ResponseEntity<?> getOrdersByUserId(@PathVariable Long userId) {
        try {
            List<Order> orders = orderService.getUserOrdersWithItems(userId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 获取用户消费统计
     * @return 用户消费列表
     */
    @GetMapping("/orders/user-sales-statistics")
    public ResponseEntity<?> getUserSalesStatistics() {
        try {
            List<UserSalesDTO> sales = orderService.getUserSalesStatistics();
            return ResponseEntity.ok(sales);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
    
    /**
     * 根据状态获取订单（管理员用）
     * @param status 订单状态
     * @return 订单列表
     */
    @GetMapping("/admin/orders/status/{status}")
    public ResponseEntity<?> getOrdersByStatus(@PathVariable String status) {
        try {
            Order.Status orderStatus = Order.Status.valueOf(status.toUpperCase());
            List<Order> orders = orderService.getOrdersByStatus(orderStatus);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
}