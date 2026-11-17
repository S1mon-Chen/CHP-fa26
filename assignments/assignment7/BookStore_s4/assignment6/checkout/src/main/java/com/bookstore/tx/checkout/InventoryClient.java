package com.bookstore.tx.checkout;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import static com.bookstore.tx.common.Contracts.*;

@FeignClient(name = "inventory", url = "${services.inventory-url}")
public interface InventoryClient {
    @PostMapping("/internal/inventory/deduct") Deduction deduct(@RequestBody PlaceOrder request);
    @GetMapping("/internal/inventory") java.util.List<Book> books();
}
