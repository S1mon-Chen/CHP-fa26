package com.bookstore.tx.orders;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.spring.annotation.GlobalLock;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import com.bookstore.tx.common.BusinessException;
import static com.bookstore.tx.common.Contracts.*;

@Service
public class OrdersService {
    private static final Logger log = LoggerFactory.getLogger(OrdersService.class);
    private final JdbcTemplate jdbc;
    private final boolean failureEnabled;
    public OrdersService(JdbcTemplate jdbc, @Value("${demo.failure-enabled:false}") boolean failureEnabled) {
        this.jdbc = jdbc;
        this.failureEnabled = failureEnabled;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderResult create(PlaceOrder request) {
        String xid = RootContext.getXID();
        try {
            jdbc.update("INSERT INTO purchase_order(request_id,book_id,quantity,xid) VALUES (?,?,?,?)",
                    request.requestId(), request.bookId(), request.quantity(), xid);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "DUPLICATE_REQUEST", "该请求已创建订单，请查询原订单", xid, request.requestId());
        }
        if (request.failOrder() && failureEnabled) {
            log.error("SIMULATED_RUNTIME_EXCEPTION after INSERT requestId={} xid={}", request.requestId(), xid);
            // Intentionally throw after executing the INSERT. Spring rolls back this local branch;
            // Seata then automatically undoes the ALREADY COMMITTED inventory branch.
            throw new BusinessException(500, "SIMULATED_ORDER_FAILURE", "模拟订单入库 RuntimeException", xid, request.requestId());
        }
        OrderResult result = lookup(request.requestId()).order();
        log.info("ORDER_INSERTED requestId={} xid={} orderId={}", request.requestId(), xid, result.orderId());
        return result;
    }

    @GlobalLock
    @Transactional(rollbackFor = Exception.class)
    public OrderLookup lookup(String requestId) {
        var rows = jdbc.query("SELECT id,request_id,book_id,quantity,xid FROM purchase_order WHERE request_id=? FOR UPDATE",
                (rs,n) -> new OrderResult(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getInt(4),rs.getString(5),false), requestId);
        return new OrderLookup(!rows.isEmpty(), rows.isEmpty() ? null : rows.getFirst());
    }
}
