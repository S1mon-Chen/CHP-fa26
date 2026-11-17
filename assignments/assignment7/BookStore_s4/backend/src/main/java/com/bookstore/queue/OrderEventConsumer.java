package com.bookstore.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "app.orders.async-enabled", havingValue = "true")
public class OrderEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);
    private final ObjectMapper json;
    private final OrderTaskRepository tasks;
    private final OrderTaskProcessor processor;
    private final OrderStreams streams;
    private final long delayMs;

    public OrderEventConsumer(ObjectMapper json, OrderTaskRepository tasks, OrderTaskProcessor processor,
            OrderStreams streams, @Value("${app.orders.processing-delay-ms:3000}") long delayMs) {
        this.json = json; this.tasks = tasks; this.processor = processor; this.streams = streams; this.delayMs = delayMs;
    }

    @KafkaListener(topics = "order-events", groupId = "${spring.kafka.consumer.group-id:bookstore-orders-v5}")
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        OrderEvent event = json.readValue(record.value(), OrderEvent.class);
        // A reset demo DB can leave older Kafka messages behind; never create untracked orders.
        var task = tasks.findById(event.queueId());
        if (task.isEmpty()) { log.warn("Ignoring event without task: {}", event.queueId()); return; }
        log.info("KAFKA_CONSUMED queueId={} partition={} offset={}", event.queueId(), record.partition(), record.offset());
        if (!task.get().terminal()) Thread.sleep(delayMs);
        OrderStatus result;
        try { result = processor.process(event.queueId()); }
        catch (IllegalArgumentException ex) { result = processor.fail(event.queueId(), ex.getMessage()); }
        // Unexpected infrastructure failures escape: Kafka retries instead of falsely marking success.
        streams.publish(result); // transaction has committed before the client is notified
        log.info("ORDER_RESULT queueId={} status={} orderId={}", result.queueId(), result.status(), result.orderId());
    }
}
