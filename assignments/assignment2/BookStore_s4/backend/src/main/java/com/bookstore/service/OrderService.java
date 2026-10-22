package com.bookstore.service;

import com.bookstore.dto.*;
import com.bookstore.entity.*;
import com.bookstore.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orders;
    private final BookRepository books;
    private final UserService users;
    private final CurrentUser currentUser;

    public OrderService(OrderRepository orders, BookRepository books, UserService users, CurrentUser currentUser) {
        this.orders = orders;
        this.books = books;
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(rollbackFor = Exception.class)
    public Order submitOrder(OrderSubmitRequestDTO request, Long userId) throws Exception {
        currentUser.requireOwnerOrAdmin(userId);
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

    public List<Order> getUserOrders(Long userId) {
        currentUser.requireOwnerOrAdmin(userId);
        return orders.findByUserIdWithOrderItems(userId);
    }

    public List<Order> getUserOrdersWithItems(Long userId) { return getUserOrders(userId); }

    public Order getOrderById(Long orderId) {
        currentUser.require();
        Order order = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        currentUser.requireOwnerOrAdmin(order.getUserId());
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public Order cancelOrder(Long orderId) {
        currentUser.require();
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        currentUser.requireOwnerOrAdmin(order.getUserId());
        cancelPendingOrder(order);
        return order;
    }

    private void cancelPendingOrder(Order order) {
        if (order.getStatus() != Order.Status.PENDING) throw new IllegalArgumentException("只有待支付的订单可以取消");
        Map<Long, Integer> quantities = new TreeMap<>();
        for (var item : order.getOrderItems()) quantities.merge(item.getBookId(), item.getQuantity(), Math::addExact);
        for (var entry : quantities.entrySet()) {
            Book book = books.findByIdForUpdate(entry.getKey()).orElseThrow(() -> new IllegalArgumentException("书籍不存在"));
            book.setStock(Math.addExact(book.getStock(), entry.getValue()));
        }
        order.setStatus(Order.Status.CANCELLED);
    }

    @Transactional(rollbackFor = Exception.class)
    public Order updateOrderStatus(Long orderId, Order.Status status) {
        currentUser.requireAdmin();
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (order.getStatus() == status) return order;
        if (status == Order.Status.CANCELLED) {
            cancelPendingOrder(order);
        } else if ((order.getStatus() == Order.Status.PENDING && status == Order.Status.PAID)
                || (order.getStatus() == Order.Status.PAID && status == Order.Status.DELIVERED)) {
            order.setStatus(status);
        } else {
            throw new IllegalArgumentException("不允许该订单状态变更");
        }
        return order;
    }

    public List<Order> getAllOrders() { return getAllOrdersWithItems(); }

    public List<Order> getAllOrdersWithItems() {
        currentUser.requireAdmin();
        return orders.findAllWithOrderItems();
    }

    public List<Order> getOrdersByStatus(Order.Status status) {
        currentUser.requireAdmin();
        return orders.findByStatus(status);
    }

    private List<Order> completedSales() {
        currentUser.requireAdmin();
        return orders.findSalesWithItems(List.of(Order.Status.PAID, Order.Status.DELIVERED));
    }

    public List<BookSalesDTO> getBookSalesStatistics() {
        Map<Long, BookSalesDTO> sales = new HashMap<>();
        for (Order order : completedSales()) {
            for (OrderItem item : order.getOrderItems()) {
                Book book = item.getBook();
                BookSalesDTO row = sales.computeIfAbsent(book.getId(), id ->
                        new BookSalesDTO(id, book.getTitle(), book.getAuthor(), 0, BigDecimal.ZERO));
                row.setTotalQuantity(row.getTotalQuantity() + item.getQuantity());
                row.setTotalAmount(row.getTotalAmount().add(item.getSubtotal()));
            }
        }
        return sales.values().stream().sorted(Comparator.comparing(BookSalesDTO::getTotalQuantity).reversed()).toList();
    }

    public List<UserSalesDTO> getUserSalesStatistics() {
        Map<Long, UserSalesDTO> sales = new HashMap<>();
        for (Order order : completedSales()) {
            User user = order.getUser();
            UserSalesDTO row = sales.computeIfAbsent(user.getId(), id -> new UserSalesDTO(id, user.getUsername(), 0, BigDecimal.ZERO));
            row.setTotalOrders(row.getTotalOrders() + 1);
            row.setTotalAmount(row.getTotalAmount().add(order.getTotalAmount()));
        }
        return sales.values().stream().sorted(Comparator.comparing(UserSalesDTO::getTotalAmount).reversed()).toList();
    }
}
