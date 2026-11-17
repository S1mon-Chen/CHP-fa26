package com.bookstore.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookCacheService {
    public static final String PREFIX = "a7:book:";
    // A real stored value, distinct from Redis GET returning null for a missing key.
    public static final String NULL_VALUE = "__NULL__";
    public record Lookup(Book book, String source) { }
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final BookRepository repository;
    private final TransactionTemplate transactions;
    private final Metrics metrics;
    private final long ttl;
    private final long jitter;
    private final long nullTtl;

    public BookCacheService(StringRedisTemplate redis, ObjectMapper json, BookRepository repository,
            TransactionTemplate transactions, Metrics metrics,
            @Value("${book.cache.ttl-seconds}") long ttl,
            @Value("${book.cache.jitter-seconds}") long jitter,
            @Value("${book.cache.null-ttl-seconds}") long nullTtl) {
        if (ttl <= 0 || nullTtl <= 0 || jitter < 0) throw new IllegalArgumentException("Invalid cache TTL");
        this.redis = redis;
        this.json = json;
        this.repository = repository;
        this.transactions = transactions;
        this.metrics = metrics;
        this.ttl = ttl;
        this.jitter = jitter;
        this.nullTtl = nullTtl;
    }

    public Lookup find(long id) {
        String key = PREFIX + id;
        String cached = redis.opsForValue().get(key);
        if (NULL_VALUE.equals(cached)) {
            metrics.nullHits.increment();
            return new Lookup(null, "NULL_HIT");
        }
        if (cached != null) {
            try {
                Book book = json.readValue(cached, Book.class);
                if (book == null || book.id() != id) throw new IllegalArgumentException("Invalid cached book");
                metrics.cacheHits.increment();
                return new Lookup(book, "HIT");
            } catch (JsonProcessingException | IllegalArgumentException corrupt) {
                redis.delete(key); // Rebuild an invalid entry from the source of truth.
            }
        }
        metrics.cacheMisses.increment();
        Book book = repository.find(id).orElse(null);
        if (book == null) {
            redis.opsForValue().set(key, NULL_VALUE, Duration.ofSeconds(nullTtl));
            return new Lookup(null, "NULL_MISS");
        }
        try {
            redis.opsForValue().set(key, json.writeValueAsString(book),
                    Duration.ofSeconds(ttl + ThreadLocalRandom.current().nextLong(jitter + 1)));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Cannot serialize book", impossible);
        }
        return new Lookup(book, "MISS");
    }

    public Book save(long id, Book.Write body) {
        // execute() returns only after the database transaction has COMMITTED.
        Book saved = transactions.execute(status -> repository.save(id, body));
        try {
            redis.delete(PREFIX + id); // Also removes a prior negative cache entry.
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw new CacheInvalidationException(unavailable);
        }
        return saved;
    }

    public static class CacheInvalidationException extends RuntimeException {
        public CacheInvalidationException(Throwable cause) { super(cause); }
    }
}
