package com.bookstore.cache;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookRepository {
    private final JdbcTemplate jdbc;
    private final Metrics metrics;
    public BookRepository(JdbcTemplate jdbc, Metrics metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }
    public Optional<Book> find(long id) {
        metrics.dbReads.increment();
        return jdbc.query("SELECT id,title,author,price,stock FROM books WHERE id=?",
                (rs, row) -> new Book(rs.getLong("id"), rs.getString("title"),
                        rs.getString("author"), rs.getBigDecimal("price"), rs.getInt("stock")), id)
                .stream().findFirst();
    }
    public Book save(long id, Book.Write body) {
        jdbc.update("""
                INSERT INTO books (id,title,author,price,stock) VALUES (?,?,?,?,?)
                ON DUPLICATE KEY UPDATE title=?,author=?,price=?,stock=?
                """, id, body.title(), body.author(), body.price(), body.stock(),
                body.title(), body.author(), body.price(), body.stock());
        return new Book(id, body.title(), body.author(), body.price(), body.stock());
    }
}
