package com.bookstore.tx.checkout;

import jakarta.validation.Valid;
import org.slf4j.*;
import org.springframework.web.bind.annotation.*;
import static com.bookstore.tx.common.Contracts.*;

@RestController
public class CheckoutController {
    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);
    private final CheckoutService service;
    private final InventoryClient inventory;
    private final OrdersClient orders;
    public CheckoutController(CheckoutService service, InventoryClient inventory, OrdersClient orders) {
        this.service = service; this.inventory = inventory; this.orders = orders;
    }
    @PostMapping("/api/orders") public OrderResult place(@Valid @RequestBody PlaceOrder request) {
        OrderResult result = service.place(request);
        log.info("GLOBAL_COMMITTED requestId={} xid={} orderId={} replayed={}",
                request.requestId(), result.xid(), result.orderId(), result.replayed());
        return result;
    }
    @GetMapping("/api/books") public java.util.List<Book> books() { return inventory.books(); }
    @GetMapping("/api/orders/{requestId}") public OrderLookup lookup(@PathVariable String requestId) { return orders.lookup(requestId); }
    @GetMapping("/health") public java.util.Map<String,String> health() { return java.util.Map.of("service","checkout","status","UP"); }
}
