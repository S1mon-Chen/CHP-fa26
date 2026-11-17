package com.bookstore.cache;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

@Component
public class Metrics {
    final LongAdder dbReads = new LongAdder();
    final LongAdder cacheHits = new LongAdder();
    final LongAdder nullHits = new LongAdder();
    final LongAdder cacheMisses = new LongAdder();
    final LongAdder admitted = new LongAdder();
    final LongAdder blocked = new LongAdder();
    public Map<String, Long> snapshot() {
        return Map.of("dbReads", dbReads.sum(), "cacheHits", cacheHits.sum(),
                "nullHits", nullHits.sum(), "cacheMisses", cacheMisses.sum(),
                "admitted", admitted.sum(), "blocked", blocked.sum());
    }
}
