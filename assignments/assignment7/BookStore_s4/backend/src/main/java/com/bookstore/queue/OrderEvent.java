package com.bookstore.queue;

import com.bookstore.dto.OrderSubmitRequestDTO;

/** JSON carried by Kafka; identity is assigned on the server, never accepted from the request body. */
public record OrderEvent(String queueId, Long userId, OrderSubmitRequestDTO order) {}
