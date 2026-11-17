package com.bookstore.queue;

import java.time.Instant;

public record OrderStatus(String queueId, String status, String message, Long orderId,
                          Instant createdAt, Instant finishedAt) {
    public boolean terminal() { return !status.equals("QUEUED"); }
}
