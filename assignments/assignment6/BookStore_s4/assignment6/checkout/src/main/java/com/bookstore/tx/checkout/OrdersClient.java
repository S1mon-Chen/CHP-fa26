package com.bookstore.tx.checkout;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import static com.bookstore.tx.common.Contracts.*;

@FeignClient(name = "orders", url = "${services.orders-url}")
public interface OrdersClient {
    @PostMapping("/internal/orders") OrderResult create(@RequestBody PlaceOrder request);
    @GetMapping("/internal/orders/{requestId}") OrderLookup lookup(@PathVariable("requestId") String requestId);
}
