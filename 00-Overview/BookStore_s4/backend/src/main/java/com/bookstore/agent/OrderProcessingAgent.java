package com.bookstore.agent;

import com.bookstore.dto.OrderSubmitRequestDTO;
import com.bookstore.entity.Order;
import com.bookstore.service.BookService;
import com.bookstore.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单处理智能体
 * 负责处理下单、查询订单状态、取消订单等操作
 */
@Component
public class OrderProcessingAgent extends AbstractAgent {

    @Autowired
    private OrderService orderService;

    @Autowired
    private BookService bookService;

    /**
     * 判断是否能处理该意图
     * @param intent 用户意图字符串
     * @return 如果能处理返回 true，否则返回 false
     */
    @Override
    public boolean canHandle(String intent) {
        if (intent == null) {
            return false;
        }
        String intentLower = intent.toLowerCase();
        return intentLower.contains("下单") || 
               intentLower.contains("购买") || 
               intentLower.contains("订单") || 
               intentLower.contains("取消") ||
               intentLower.contains("支付");
    }

    /**
     * 执行智能体逻辑
     * @param input 用户输入
     * @param context 上下文信息
     * @return 智能体执行结果
     */
    @Override
    protected AgentResponse doExecute(String input, Map<String, Object> context) {
        try {
            // 检查用户是否已登录
            Long userId = getUserId(context);
            if (userId == null) {
                return AgentResponse.failure("需要先登录才能进行订单操作，请先登录账户。", context);
            }

            String inputLower = input.toLowerCase();

            // 判断操作类型
            if (inputLower.contains("取消")) {
                return handleCancelOrder(input, context);
            } else if (inputLower.contains("查询") || inputLower.contains("状态") || 
                       inputLower.contains("查看") || inputLower.contains("列表")) {
                return handleQueryOrders(userId, context);
            } else {
                return handleCreateOrder(input, context);
            }

        } catch (Exception e) {
            logError("订单处理智能体执行异常: {}", e.getMessage());
            return AgentResponse.failure("订单服务暂时不可用，请稍后再试。", context);
        }
    }

    /**
     * 处理创建订单请求
     */
    private AgentResponse handleCreateOrder(String input, Map<String, Object> context) {
        try {
            // 从上下文获取选中的书籍
            Object selectedBookObj = context.get("selectedBook");
            
            if (selectedBookObj == null) {
                return AgentResponse.success("请问您想购买哪一本书？", context);
            }

            // 处理 Map 类型的 selectedBook
            Map<String, Object> selectedBook;
            if (selectedBookObj instanceof Map) {
                selectedBook = (Map<String, Object>) selectedBookObj;
            } else {
                logError("selectedBook 类型错误: {}", selectedBookObj.getClass().getName());
                return AgentResponse.failure("书籍信息格式错误，请重新选择。", context);
            }

            // 安全地获取书籍 ID（处理 Integer 和 Long 类型）
            Long bookId = null;
            Object idObj = selectedBook.get("id");
            if (idObj instanceof Long) {
                bookId = (Long) idObj;
            } else if (idObj instanceof Integer) {
                bookId = ((Integer) idObj).longValue();
            } else if (idObj instanceof String) {
                bookId = Long.parseLong((String) idObj);
            } else {
                logError("无法解析书籍ID，类型: {}", idObj != null ? idObj.getClass().getName() : "null");
                return AgentResponse.failure("无法获取书籍ID，请重新选择。", context);
            }

            String bookTitle = (String) selectedBook.get("title");

            // 创建订单请求
            OrderSubmitRequestDTO.OrderItemDTO itemDTO = new OrderSubmitRequestDTO.OrderItemDTO();
            itemDTO.setBookId(bookId);
            itemDTO.setQuantity(1);

            List<OrderSubmitRequestDTO.OrderItemDTO> items = new ArrayList<>();
            items.add(itemDTO);

            OrderSubmitRequestDTO orderRequest = new OrderSubmitRequestDTO();
            orderRequest.setItems(items);

            // 获取用户ID并提交订单
            Long userId = getUserId(context);
            Order order = orderService.submitOrder(orderRequest, userId);

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("订单创建成功！\n\n")
                    .append("订单号：").append(order.getId()).append("\n")
                    .append("商品：").append(bookTitle).append("\n")
                    .append("数量：1\n")
                    .append("金额：¥").append(order.getTotalAmount()).append("\n")
                    .append("状态：待支付\n")
                    .append("预计送达时间：3-5个工作日");

            // 更新上下文
            Map<String, Object> newContext = new HashMap<>(context);
            newContext.put("orderId", order.getId());
            newContext.put("orderAmount", order.getTotalAmount());

            return AgentResponse.success(resultBuilder.toString(), newContext, "PromotionExpertAgent");

        } catch (Exception e) {
            logError("创建订单失败: {}", e.getMessage());
            return AgentResponse.failure("创建订单失败: " + e.getMessage(), context);
        }
    }

    /**
     * 处理查询订单请求
     */
    private AgentResponse handleQueryOrders(Long userId, Map<String, Object> context) {
        try {
            List<Order> orders = orderService.getUserOrdersWithItems(userId);

            if (orders.isEmpty()) {
                return AgentResponse.success("您还没有任何订单。", context);
            }

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("您的订单列表：\n\n");

            int index = 1;
            for (Order order : orders) {
                resultBuilder.append(index++).append(". 订单号：").append(order.getId()).append("\n")
                        .append("   金额：¥").append(order.getTotalAmount()).append("\n")
                        .append("   状态：").append(getStatusDescription(order.getStatus())).append("\n")
                        .append("   商品：");

                List<String> productNames = new ArrayList<>();
                order.getOrderItems().forEach(item -> {
                    productNames.add(item.getBook().getTitle() + " x " + item.getQuantity());
                });
                resultBuilder.append(String.join(", ", productNames)).append("\n\n");
            }

            return AgentResponse.success(resultBuilder.toString(), context);

        } catch (Exception e) {
            logError("查询订单失败: {}", e.getMessage());
            return AgentResponse.failure("查询订单失败: " + e.getMessage(), context);
        }
    }

    /**
     * 处理取消订单请求
     */
    private AgentResponse handleCancelOrder(String input, Map<String, Object> context) {
        try {
            // 从上下文获取订单ID，或从输入中提取
            Object orderIdObj = context != null ? context.get("orderId") : null;
            Long orderId = null;

            if (orderIdObj instanceof Long) {
                orderId = (Long) orderIdObj;
            } else {
                // 尝试从输入中提取订单号
                String orderIdStr = extractOrderId(input);
                if (orderIdStr != null) {
                    orderId = Long.parseLong(orderIdStr);
                }
            }

            if (orderId == null) {
                return AgentResponse.success("请提供要取消的订单号。", context);
            }

            Order order = orderService.cancelOrder(orderId);

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("订单取消成功！\n\n")
                    .append("订单号：").append(order.getId()).append("\n")
                    .append("状态：已取消");

            return AgentResponse.success(resultBuilder.toString(), context);

        } catch (Exception e) {
            logError("取消订单失败: {}", e.getMessage());
            return AgentResponse.failure("取消订单失败: " + e.getMessage(), context);
        }
    }

    /**
     * 获取状态描述
     */
    private String getStatusDescription(Order.Status status) {
        if (status == Order.Status.PENDING) {
            return "待支付";
        } else if (status == Order.Status.PAID) {
            return "已支付";
        } else if (status == Order.Status.DELIVERED) {
            return "已送达";
        } else if (status == Order.Status.CANCELLED) {
            return "已取消";
        }
        return "未知状态";
    }

    /**
     * 从输入中提取订单号
     */
    private String extractOrderId(String input) {
        // 简单实现：提取连续的数字
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}