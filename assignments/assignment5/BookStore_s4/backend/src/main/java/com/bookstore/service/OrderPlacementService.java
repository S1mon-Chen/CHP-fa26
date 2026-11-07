package com.bookstore.service;

import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.entity.*;
import com.bookstore.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Internal inventory transaction. Callers must obtain userId from a trusted request or stored queue task. */
@Service
public class OrderPlacementService {
    private final OrderRepository orders;
    private final BookRepository books;
    private final UserService users;

    public OrderPlacementService(OrderRepository orders, BookRepository books, UserService users) {
        this.orders = orders; this.books = books; this.users = users;
    }

    @Transactional(rollbackFor = Exception.class)
    public Order place(OrderSubmitRequestDTO request, Long userId) throws Exception {
        User user = users.getUserById(userId);
        if (user.getStatus() != User.Status.ACTIVE) throw new IllegalArgumentException("账号已被禁用");
        if (request == null || request.getItems() == null || request.getItems().isEmpty())
            throw new IllegalArgumentException("订单不能为空");

        // Aggregate duplicate rows and lock books in a stable order to avoid cross-cart deadlocks.
        Map<Long, Integer> quantities = new TreeMap<>();
        for (var item : request.getItems()) {
            if (item == null || item.getBookId() == null || item.getBookId() <= 0
                    || item.getQuantity() == null || item.getQuantity() <= 0)
                throw new IllegalArgumentException("书籍ID及购买数量必须为正整数");
            try { quantities.merge(item.getBookId(), item.getQuantity(), Math::addExact); }
            catch (ArithmeticException e) { throw new IllegalArgumentException("购买数量过大"); }
        }

        Map<Long, Book> lockedBooks = new LinkedHashMap<>();
        for (var entry : quantities.entrySet()) {
            Book book = books.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("书籍不存在: " + entry.getKey()));
            if (book.getStock() < entry.getValue()) throw new IllegalArgumentException("书籍库存不足: " + book.getTitle());
            lockedBooks.put(entry.getKey(), book);
        }

        Order order = new Order();
        order.setUser(user); order.setUserId(user.getId()); order.setStatus(Order.Status.PENDING);
        BigDecimal total = BigDecimal.ZERO;
        for (var entry : quantities.entrySet()) {
            Book book = lockedBooks.get(entry.getKey());
            int quantity = entry.getValue();
            book.setStock(book.getStock() - quantity);
            OrderItem item = new OrderItem();
            item.setBook(book); item.setBookId(book.getId()); item.setQuantity(quantity); item.setPrice(book.getPrice());
            order.addOrderItem(item);
            total = total.add(item.getSubtotal());
        }
        order.setTotalAmount(total);
        return orders.save(order);
    }

}
