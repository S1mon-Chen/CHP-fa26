package com.bookstore.tx.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bookstore.tx.common.BusinessException;
import feign.FeignException;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.bookstore.tx.common.Contracts.*;

@Service
public class CheckoutService {
    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private final InventoryClient inventory;
    private final OrdersClient orders;
    private final ObjectMapper mapper;
    private final boolean failureEnabled;
    public CheckoutService(InventoryClient inventory, OrdersClient orders, ObjectMapper mapper,
                           @Value("${demo.failure-enabled:false}") boolean failureEnabled) {
        this.inventory = inventory; this.orders = orders; this.mapper = mapper; this.failureEnabled = failureEnabled;
    }

    @GlobalTransactional(name = "bookstore-place-order", rollbackFor = Exception.class, timeoutMills = 30000)
    public OrderResult place(PlaceOrder request) {
        String xid = RootContext.getXID();
        log.info("GLOBAL_BEGIN requestId={} xid={}", request.requestId(), xid);
        if (!failureEnabled && (request.failOrder() || request.pauseAfterInventoryMs() != 0)) {
            throw new BusinessException(400, "DEMO_DISABLED", "故障注入未启用", xid, request.requestId());
        }
        try {
            OrderLookup existing = orders.lookup(request.requestId());
            if (existing.found()) {
                OrderResult old = existing.order();
                if (old.bookId() != request.bookId() || old.quantity() != request.quantity()) {
                    throw new BusinessException(409, "IDEMPOTENCY_CONFLICT", "同一 requestId 的购买内容不能更改", xid, request.requestId());
                }
                return new OrderResult(old.orderId(),old.requestId(),old.bookId(),old.quantity(),old.xid(),true);
            }
            Deduction deduction = inventory.deduct(request);
            log.info("INVENTORY_RPC_RETURNED requestId={} xid={} stock={}->{}", request.requestId(), xid,
                    deduction.beforeStock(), deduction.afterStock());
            if (request.pauseAfterInventoryMs() > 0) {
                try { Thread.sleep(request.pauseAfterInventoryMs()); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(503,"INTERRUPTED","下单被中断",xid,request.requestId());
                }
            }
            return orders.create(request);
        } catch (FeignException e) {
            ApiError downstream = null;
            try { downstream = mapper.readValue(e.contentUTF8(), ApiError.class); } catch (Exception ignored) { /* Transport error. */ }
            int status = e.status() >= 400 && e.status() <= 599 ? e.status() : 503;
            String code = downstream == null ? "RPC_FAILURE" : downstream.code();
            String message = downstream == null ? "下游服务调用失败" : downstream.message();
            log.error("GLOBAL_ROLLBACK_TRIGGER requestId={} xid={} cause={}", request.requestId(), xid, code);
            throw new BusinessException(status, code, message, xid, request.requestId());
        }
    }
}
