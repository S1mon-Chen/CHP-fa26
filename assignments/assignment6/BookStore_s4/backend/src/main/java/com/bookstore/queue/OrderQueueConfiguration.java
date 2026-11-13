package com.bookstore.queue;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.orders.async-enabled", havingValue = "true")
public class OrderQueueConfiguration {
    @Bean
    NewTopic orderEvents() { return TopicBuilder.name("order-events").partitions(3).replicas(1).build(); }

    @Bean
    DefaultErrorHandler orderErrorHandler(ObjectMapper json, OrderTaskRepository tasks,
            OrderTaskProcessor processor, OrderStreams streams) {
        return new DefaultErrorHandler((record, error) -> {
            try {
                var event = json.readValue((String) record.value(), OrderEvent.class);
                if (tasks.existsById(event.queueId())) {
                    streams.publish(processor.fail(event.queueId(), "订单处理失败，请稍后重试"));
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                org.slf4j.LoggerFactory.getLogger(OrderQueueConfiguration.class)
                        .error("Discarding malformed order event at offset {}", record.offset());
            }
        }, new FixedBackOff(1000, 2));
    }
}
