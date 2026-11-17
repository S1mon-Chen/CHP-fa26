package com.bookstore.tx.inventory;

import org.apache.seata.core.context.RootContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bookstore.tx.common.BusinessException;
import static com.bookstore.tx.common.Contracts.*;

@Service
public class InventoryService {
    private final JdbcTemplate jdbc;
    public InventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(rollbackFor = Exception.class)
    public Deduction deduct(PlaceOrder request) {
        var books = jdbc.query("SELECT book_id,title,stock FROM inventory WHERE book_id=? FOR UPDATE",
                (rs, n) -> new Book(rs.getLong(1), rs.getString(2), rs.getInt(3)), request.bookId());
        String xid = RootContext.getXID();
        if (books.isEmpty()) throw new BusinessException(404, "BOOK_NOT_FOUND", "图书不存在", xid, request.requestId());
        Book book = books.getFirst();
        if (jdbc.update("UPDATE inventory SET stock=stock-? WHERE book_id=? AND stock>=?",
                request.quantity(), request.bookId(), request.quantity()) != 1) {
            throw new BusinessException(409, "INSUFFICIENT_STOCK", "库存不足", xid, request.requestId());
        }
        return new Deduction(book.bookId(), book.title(), request.quantity(), book.stock(), book.stock()-request.quantity(), xid);
    }

    public java.util.List<Book> books() {
        return jdbc.query("SELECT book_id,title,stock FROM inventory ORDER BY book_id",
                (rs,n) -> new Book(rs.getLong(1),rs.getString(2),rs.getInt(3)));
    }
}
