package com.bookstore.controller;

import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.entity.Order;
import com.bookstore.service.CurrentUser;
import com.bookstore.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders;
    private final CurrentUser currentUser;

    public OrderController(OrderService orders, CurrentUser currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> submitOrder(@Valid @RequestBody OrderSubmitRequestDTO request) throws Exception {
        Order order = orders.submitOrder(request, currentUser.require().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "订单创建成功", "order", order));
    }

    @GetMapping("/orders")
    public Object getUserOrders() { return orders.getUserOrdersWithItems(currentUser.require().getId()); }

    @GetMapping("/orders/{orderId}")
    public Object getOrderById(@PathVariable Long orderId) { return orders.getOrderById(orderId); }

    @PostMapping("/orders/{orderId}/cancel")
    public Object cancelOrder(@PathVariable Long orderId) {
        return Map.of("message", "订单已取消", "order", orders.cancelOrder(orderId));
    }

    @PutMapping("/admin/orders/{orderId}/status")
    public Object updateOrderStatus(@PathVariable Long orderId, @RequestParam String status) {
        return Map.of("message", "订单状态更新成功", "order", orders.updateOrderStatus(orderId, Order.Status.valueOf(status.toUpperCase())));
    }

    @GetMapping({"/admin/orders", "/orders/all"})
    public Object getAllOrders() { return orders.getAllOrdersWithItems(); }

    @GetMapping("/orders/sales-statistics")
    public Object getBookSalesStatistics() { return orders.getBookSalesStatistics(); }

    @GetMapping("/orders/user/{userId}")
    public Object getOrdersByUserId(@PathVariable Long userId) { return orders.getUserOrdersWithItems(userId); }

    @GetMapping("/orders/user-sales-statistics")
    public Object getUserSalesStatistics() { return orders.getUserSalesStatistics(); }

    @GetMapping("/admin/orders/status/{status}")
    public Object getOrdersByStatus(@PathVariable String status) { return orders.getOrdersByStatus(Order.Status.valueOf(status.toUpperCase())); }
}
