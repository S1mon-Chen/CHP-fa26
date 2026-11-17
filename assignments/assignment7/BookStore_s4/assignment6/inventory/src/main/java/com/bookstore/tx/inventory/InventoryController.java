package com.bookstore.tx.inventory;

import jakarta.validation.Valid;
import org.slf4j.*;
import org.springframework.web.bind.annotation.*;
import static com.bookstore.tx.common.Contracts.*;

@RestController
public class InventoryController {
    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    private final InventoryService service;
    public InventoryController(InventoryService service) { this.service = service; }

    @PostMapping("/internal/inventory/deduct")
    public Deduction deduct(@Valid @RequestBody PlaceOrder request) {
        Deduction result = service.deduct(request);
        // The separate transactional service proxy has returned: the local branch has committed.
        log.info("INVENTORY_BRANCH_COMMITTED requestId={} xid={} before={} after={}",
                request.requestId(), result.xid(), result.beforeStock(), result.afterStock());
        return result;
    }
    @GetMapping("/internal/inventory") public java.util.List<Book> books() { return service.books(); }
    @GetMapping("/health") public java.util.Map<String,String> health() { return java.util.Map.of("service","inventory","status","UP"); }
}
