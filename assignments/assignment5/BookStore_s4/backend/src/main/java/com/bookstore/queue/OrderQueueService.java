package com.bookstore.queue;

import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.service.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "app.orders.async-enabled", havingValue = "true")
public class OrderQueueService {
    private static final Logger log = LoggerFactory.getLogger(OrderQueueService.class);
    private final KafkaTemplate<String, String> kafka;
    private final OrderTaskRepository tasks;
    private final OrderTaskProcessor processor;
    private final ObjectMapper json;
    private final CurrentUser currentUser;

    public OrderQueueService(KafkaTemplate<String, String> kafka, OrderTaskRepository tasks,
            OrderTaskProcessor processor, ObjectMapper json, CurrentUser currentUser) {
        this.kafka = kafka; this.tasks = tasks; this.processor = processor; this.json = json; this.currentUser = currentUser;
    }

    public OrderStatus submit(OrderSubmitRequestDTO request) throws Exception {
        Long userId = currentUser.require().getId();
        String id = UUID.randomUUID().toString();
        String payload = json.writeValueAsString(new OrderEvent(id, userId, request));
        OrderStatus accepted = tasks.saveAndFlush(new OrderTask(id, userId, payload)).view();
        try {
            // Only wait for broker acknowledgement; never wait for the slow inventory consumer.
            var metadata = kafka.send("order-events", id, payload).get(5, TimeUnit.SECONDS).getRecordMetadata();
            log.info("KAFKA_PRODUCED queueId={} topic={} partition={} offset={}", id,
                    metadata.topic(), metadata.partition(), metadata.offset());
            return accepted;
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            String reason = "消息队列暂时不可用，订单未受理";
            OrderStatus outcome = processor.fail(id, reason);
            // A lost/late producer ACK can race a completed consumer transaction.
            // In that case the event was accepted; preserve its result instead of inviting another POST.
            if (!outcome.message().equals(reason)) return accepted;
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "消息队列暂时不可用，请稍后重试");
        }
    }

    public OrderStatus status(String id) {
        OrderTask task = tasks.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "排队任务不存在"));
        currentUser.requireOwnerOrAdmin(task.userId);
        return task.view();
    }
}
