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
import com.bookstore.queue.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orders;
    private final CurrentUser currentUser;

    private final ObjectProvider<OrderQueueService> queue;
    private final OrderStreams streams;

    public OrderController(OrderService orders, CurrentUser currentUser,
            ObjectProvider<OrderQueueService> queue, OrderStreams streams) {
        this.queue = queue; this.streams = streams;
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> submitOrder(@Valid @RequestBody OrderSubmitRequestDTO request) throws Exception {
        if (queue.getIfAvailable() != null) {
            var accepted = queue.getObject().submit(request);
            return ResponseEntity.accepted()
                    .header("Location", "/api/orders/" + accepted.queueId() + "/status")
                    .body(accepted);
        }
        Order order = orders.submitOrder(request, currentUser.require().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "订单创建成功", "order", order));
    }

    private OrderQueueService requireQueue() {
        if (queue.getIfAvailable() == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "未启用异步订单");
        return queue.getObject();
    }

    @GetMapping("/orders/{id}/status")
    public OrderStatus queuedStatus(@PathVariable String id) { return requireQueue().status(id); }

    @GetMapping(value = "/orders/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable String id) {
        var service = requireQueue();
        service.status(id); // authorize before opening the streaming response
        var emitter = streams.subscribe(id, () -> service.status(id));
        return ResponseEntity.ok().header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no").body(emitter);
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
