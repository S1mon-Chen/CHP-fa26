package com.bookstore.queue;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

/** Single-backend SSE fanout. Register + read snapshot is serialized with publish to avoid lost completions. */
@Component
public class OrderStreams {
    private final Map<String, Set<SseEmitter>> listeners = new HashMap<>();

    public synchronized SseEmitter subscribe(String id, Supplier<OrderStatus> snapshot) {
        OrderStatus initial = snapshot.get();
        SseEmitter emitter = new SseEmitter(120_000L);
        listeners.computeIfAbsent(id, ignored -> new HashSet<>()).add(emitter);
        emitter.onCompletion(() -> remove(id, emitter));
        emitter.onTimeout(() -> { remove(id, emitter); emitter.complete(); });
        emitter.onError(error -> remove(id, emitter));
        send(id, emitter, initial);
        return emitter;
    }

    public synchronized void publish(OrderStatus status) {
        for (var emitter : new ArrayList<>(listeners.getOrDefault(status.queueId(), Set.of()))) {
            send(status.queueId(), emitter, status);
        }
    }

    private void send(String id, SseEmitter emitter, OrderStatus status) {
        try {
            emitter.send(SseEmitter.event().name("order-status").id(id + ":" + status.status())
                    .reconnectTime(1000).data(status));
            if (status.terminal()) { remove(id, emitter); emitter.complete(); }
        } catch (IOException | IllegalStateException ex) {
            remove(id, emitter);
            emitter.completeWithError(ex);
        }
    }

    @Scheduled(fixedDelay = 15000)
    public synchronized void heartbeat() {
        for (var entry : new HashMap<>(listeners).entrySet()) {
            for (var emitter : new ArrayList<>(entry.getValue())) {
                try { emitter.send(SseEmitter.event().comment("heartbeat")); }
                catch (IOException | IllegalStateException ex) {
                    remove(entry.getKey(), emitter); emitter.completeWithError(ex);
                }
            }
        }
    }

    private synchronized void remove(String id, SseEmitter emitter) {
        var set = listeners.get(id);
        if (set != null) { set.remove(emitter); if (set.isEmpty()) listeners.remove(id); }
    }
}
