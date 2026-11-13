package com.bookstore.queue;

import com.bookstore.service.OrderPlacementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTaskProcessor {
    private final OrderTaskRepository tasks;
    private final OrderPlacementService placement;
    private final ObjectMapper json;

    public OrderTaskProcessor(OrderTaskRepository tasks, OrderPlacementService placement, ObjectMapper json) {
        this.tasks = tasks; this.placement = placement; this.json = json;
    }

    /** Task completion and inventory changes commit together. Redelivery sees the terminal row. */
    @Transactional(rollbackFor = Exception.class)
    public OrderStatus process(String queueId) throws Exception {
        OrderTask task = tasks.lockById(queueId).orElseThrow(() -> new IllegalArgumentException("排队任务不存在"));
        if (task.terminal()) return task.view();
        OrderEvent event = json.readValue(task.eventJson, OrderEvent.class);
        var order = placement.place(event.order(), task.userId);
        task.finish("SUCCEEDED", "库存扣减完成，订单创建成功", order.getId());
        return task.view();
    }

    /** Separate transaction, invoked only after the inventory transaction has rolled back. */
    @Transactional
    public OrderStatus fail(String queueId, String reason) {
        OrderTask task = tasks.lockById(queueId).orElseThrow();
        if (!task.terminal()) task.finish("FAILED", reason.substring(0, Math.min(reason.length(), 500)), null);
        return task.view();
    }
}
