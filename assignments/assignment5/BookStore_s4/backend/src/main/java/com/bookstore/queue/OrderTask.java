package com.bookstore.queue;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_tasks")
public class OrderTask {
    @Id @Column(length = 36) String id;
    @Column(nullable = false) Long userId;
    @Lob @Column(nullable = false) String eventJson;
    @Column(nullable = false, length = 16) String state = "QUEUED";
    @Column(nullable = false, length = 500) String message = "订单已进入队列，等待库存处理";
    Long orderId;
    @Column(nullable = false) Instant createdAt = Instant.now();
    Instant finishedAt;

    protected OrderTask() {}
    OrderTask(String id, Long userId, String eventJson) {
        this.id = id; this.userId = userId; this.eventJson = eventJson;
    }
    boolean terminal() { return !state.equals("QUEUED"); }
    OrderStatus view() { return new OrderStatus(id, state, message, orderId, createdAt, finishedAt); }
    void finish(String state, String message, Long orderId) {
        this.state = state; this.message = message; this.orderId = orderId; this.finishedAt = Instant.now();
    }
}
