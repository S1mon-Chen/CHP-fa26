package com.bookstore.tx.orders;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import static com.bookstore.tx.common.Contracts.*;

@RestController
public class OrdersController {
    private final OrdersService service;
    public OrdersController(OrdersService service) { this.service = service; }
    @PostMapping("/internal/orders") public OrderResult create(@Valid @RequestBody PlaceOrder request) { return service.create(request); }
    @GetMapping("/internal/orders/{requestId}") public OrderLookup lookup(@PathVariable String requestId) { return service.lookup(requestId); }
    @GetMapping("/health") public java.util.Map<String,String> health() { return java.util.Map.of("service","orders","status","UP"); }
}
