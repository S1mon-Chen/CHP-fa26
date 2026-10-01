package com.bookstore.service;

import com.bookstore.dto.BookSalesDTO;
import com.bookstore.dto.UserSalesDTO;
import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.entity.Book;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderItem;
import com.bookstore.entity.User;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单服务类
 * 实现订单相关的业务逻辑
 */
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BookRepository bookRepository;
    private final UserService userService;
    
    @Autowired
    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository, 
                       BookRepository bookRepository, UserService userService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.bookRepository = bookRepository;
        this.userService = userService;
    }
    
    /**
     * 提交订单
     * 在一个事务中完成：创建订单 -> 扣减库存 -> 生成订单详情
     * @param orderSubmitRequestDTO 订单提交请求DTO
     * @return 创建的订单
     * @throws Exception 订单提交失败异常
     */
    @Transactional
    public Order submitOrder(OrderSubmitRequestDTO orderSubmitRequestDTO, Long userId) throws Exception {
        // 根据传入的用户ID获取用户
        User user = userService.getUserById(userId);
        
        // 验证订单数据
        if (orderSubmitRequestDTO.getItems() == null || orderSubmitRequestDTO.getItems().isEmpty()) {
            throw new Exception("订单不能为空");
        }
        
        // 计算总金额
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        
        // 处理订单项，检查库存并扣减
        for (OrderSubmitRequestDTO.OrderItemDTO itemDTO : orderSubmitRequestDTO.getItems()) {
            // 获取书籍信息（使用悲观锁防止并发问题）
            Book book = bookRepository.findById(itemDTO.getBookId())
                .orElseThrow(() -> new Exception("书籍不存在: " + itemDTO.getBookId()));
            
            // 检查库存
            if (book.getStock() < itemDTO.getQuantity()) {
                throw new Exception("书籍库存不足: " + book.getTitle());
            }
            
            // 扣减库存
            book.setStock(book.getStock() - itemDTO.getQuantity());
            bookRepository.save(book);
            
            // 计算小计
            BigDecimal subtotal = book.getPrice().multiply(BigDecimal.valueOf(itemDTO.getQuantity()));
            totalAmount = totalAmount.add(subtotal);
            
            // 创建订单项
            OrderItem orderItem = new OrderItem();
            orderItem.setBook(book);
            orderItem.setQuantity(itemDTO.getQuantity());
            orderItem.setPrice(book.getPrice());
            orderItems.add(orderItem);
        }
        
        // 创建订单
        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(totalAmount);
        order.setStatus(Order.Status.PENDING);
        
        // 保存订单
        Order savedOrder = orderRepository.save(order);
        
        // 保存订单项
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrder(savedOrder);
            orderItemRepository.save(orderItem);
        }
        
        return savedOrder;
    }
    
    /**
     * 获取用户的订单
     * @param userId 用户ID
     * @return 订单列表
     */
    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * 获取用户的订单（包含订单项）
     * @param userId 用户ID
     * @return 订单列表
     */
    public List<Order> getUserOrdersWithItems(Long userId) {
        return orderRepository.findByUserIdWithOrderItems(userId);
    }
    
    /**
     * 获取订单详情
     * @param orderId 订单ID
     * @return 订单对象
     * @throws Exception 订单不存在异常
     */
    public Order getOrderById(Long orderId) throws Exception {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new Exception("订单不存在: " + orderId));
    }
    
    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param status 新状态
     * @return 更新后的订单
     * @throws Exception 订单不存在异常
     */
    @Transactional
    public Order updateOrderStatus(Long orderId, Order.Status status) throws Exception {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new Exception("订单不存在: " + orderId));
        
        order.setStatus(status);
        return orderRepository.save(order);
    }
    
    /**
     * 取消订单
     * @param orderId 订单ID
     * @return 取消后的订单
     * @throws Exception 订单不存在或无法取消异常
     */
    @Transactional
    public Order cancelOrder(Long orderId) throws Exception {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new Exception("订单不存在: " + orderId));
        
        // 只有待支付状态的订单可以取消
        if (order.getStatus() != Order.Status.PENDING) {
            throw new Exception("只有待支付的订单可以取消");
        }
        
        // 恢复库存
        for (OrderItem orderItem : order.getOrderItems()) {
            Book book = bookRepository.findById(orderItem.getBookId())
                .orElseThrow(() -> new Exception("书籍不存在: " + orderItem.getBookId()));
            book.setStock(book.getStock() + orderItem.getQuantity());
            bookRepository.save(book);
        }
        
        // 更新订单状态
        order.setStatus(Order.Status.CANCELLED);
        return orderRepository.save(order);
    }
    
    /**
     * 获取所有订单（管理员用）
     * @return 订单列表
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    
    /**
     * 根据状态获取订单（管理员用）
     * @param status 订单状态
     * @return 订单列表
     */
    public List<Order> getOrdersByStatus(Order.Status status) {
        return orderRepository.findByStatus(status);
    }
    
    /**
     * 获取所有订单（包含订单项）
     * @return 订单列表
     */
    public List<Order> getAllOrdersWithItems() {
        return orderRepository.findAllWithOrderItems();
    }
    
    /**
     * 获取书籍销量统计
     * @return 书籍销量列表
     */
    public List<BookSalesDTO> getBookSalesStatistics() {
        List<Order> orders = getAllOrdersWithItems();
        
        Map<Long, BookSalesDTO> salesMap = new HashMap<>();
        
        for (Order order : orders) {
            for (OrderItem orderItem : order.getOrderItems()) {
                Long bookId = orderItem.getBookId();
                Book book = orderItem.getBook();
                
                BookSalesDTO salesDTO = salesMap.get(bookId);
                if (salesDTO == null) {
                    salesDTO = new BookSalesDTO(
                        bookId,
                        book.getTitle(),
                        book.getAuthor(),
                        0,
                        BigDecimal.ZERO
                    );
                    salesMap.put(bookId, salesDTO);
                }
                
                salesDTO.setTotalQuantity(salesDTO.getTotalQuantity() + orderItem.getQuantity());
                salesDTO.setTotalAmount(salesDTO.getTotalAmount().add(orderItem.getSubtotal()));
            }
        }
        
        return salesMap.values().stream()
            .sorted((a, b) -> b.getTotalQuantity().compareTo(a.getTotalQuantity()))
            .collect(Collectors.toList());
    }
    
    /**
     * 获取用户消费统计
     * @return 用户消费列表
     */
    public List<UserSalesDTO> getUserSalesStatistics() {
        List<Order> orders = getAllOrdersWithItems();
        
        Map<Long, UserSalesDTO> salesMap = new HashMap<>();
        
        for (Order order : orders) {
            Long userId = order.getUserId();
            String username = order.getUser() != null ? order.getUser().getUsername() : "未知用户";
            BigDecimal totalAmount = order.getTotalAmount();
            
            UserSalesDTO salesDTO = salesMap.get(userId);
            if (salesDTO == null) {
                salesDTO = new UserSalesDTO(
                    userId,
                    username,
                    0,
                    BigDecimal.ZERO
                );
                salesMap.put(userId, salesDTO);
            }
            
            salesDTO.setTotalOrders(salesDTO.getTotalOrders() + 1);
            salesDTO.setTotalAmount(salesDTO.getTotalAmount().add(totalAmount));
        }
        
        return salesMap.values().stream()
            .sorted((a, b) -> b.getTotalAmount().compareTo(a.getTotalAmount()))
            .collect(Collectors.toList());
    }
}