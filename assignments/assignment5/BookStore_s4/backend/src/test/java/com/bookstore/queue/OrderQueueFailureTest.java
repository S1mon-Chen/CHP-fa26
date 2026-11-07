package com.bookstore.queue;

import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.entity.User;
import com.bookstore.service.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderQueueFailureTest {
    @SuppressWarnings("unchecked")
    @Test void failedBrokerAcknowledgementIs503AndTaskCannotBeFulfilled() throws Exception {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var tasks = mock(OrderTaskRepository.class);
        var processor = mock(OrderTaskProcessor.class);
        var current = mock(CurrentUser.class);
        User user = new User(); user.setId(7L);
        when(current.require()).thenReturn(user);
        when(tasks.saveAndFlush(any(OrderTask.class))).thenAnswer(call -> call.getArgument(0));
        when(kafka.send(eq("order-events"), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        when(processor.fail(anyString(), anyString())).thenAnswer(call -> new OrderStatus(
                call.getArgument(0), "FAILED", call.getArgument(1), null, java.time.Instant.now(), java.time.Instant.now()));
        var service = new OrderQueueService(kafka, tasks, processor, new ObjectMapper(), current);
        var request = new OrderSubmitRequestDTO(List.of(new OrderSubmitRequestDTO.OrderItemDTO(1L, 1)));
        assertThatThrownBy(() -> service.submit(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        verify(processor).fail(anyString(), contains("消息队列暂时不可用"));
        verify(processor, never()).process(anyString());
        // If the broker ACK was lost after consumption committed, the caller still receives acceptance.
        when(processor.fail(anyString(), anyString())).thenAnswer(call -> new OrderStatus(
                call.getArgument(0), "SUCCEEDED", "库存扣减完成，订单创建成功", 42L, java.time.Instant.now(), java.time.Instant.now()));
        assertThat(service.submit(request).status()).isEqualTo("QUEUED");
    }
}
