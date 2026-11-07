package com.bookstore.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MCPService {

    @Autowired
    private BookService bookService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    // 执行指定类型的动作
    public String executeAction(String actionType, Map<String, Object> params) {
        switch (actionType) {
            case "order_management":
                return executeOrderManagement(params);
            case "sales_statistics":
                return executeSalesStatistics(params);
            case "book_query":
                return executeBookQuery(params);
            case "user_management":
                return executeUserManagement(params);
            default:
                return "未知的操作类型";
        }
    }

    // 验证动作的合法性
    public boolean validateAction(String actionType, Map<String, Object> params) {
        // 简单的验证逻辑，实际应用中可以更复杂
        switch (actionType) {
            case "order_management":
                return params.containsKey("userId");
            case "sales_statistics":
                return true;
            case "book_query":
                return params.containsKey("query");
            case "user_management":
                return params.containsKey("userId");
            default:
                return false;
        }
    }

    // 监控动作执行状态
    public String monitorActionExecution() {
        // 实际应用中可以返回更详细的监控信息
        return "所有动作执行正常";
    }

    // 执行订单管理操作
    private String executeOrderManagement(Map<String, Object> params) {
        Long userId = (Long) params.get("userId");
        String query = (String) params.get("query");

        if (query.contains("查看") || query.contains("查询")) {
            // 查看订单
            var orders = orderService.getUserOrders(userId);
            return "您有 " + orders.size() + " 个订单";
        } else if (query.contains("下单") || query.contains("购买")) {
            // 下单操作
            // 实际应用中需要更复杂的逻辑
            return "订单已提交，请前往购物车完成支付";
        } else {
            return "订单操作类型不明确";
        }
    }

    // 执行销售统计操作
    private String executeSalesStatistics(Map<String, Object> params) {
        String query = (String) params.get("query");

        if (query.contains("销量")) {
            // 获取销量统计
            var salesData = orderService.getBookSalesStatistics();
            return "销量统计数据已获取，共 " + salesData.size() + " 条记录";
        } else if (query.contains("用户")) {
            // 获取用户消费统计
            var userSalesData = orderService.getUserSalesStatistics();
            return "用户消费统计数据已获取，共 " + userSalesData.size() + " 条记录";
        } else {
            return "统计类型不明确";
        }
    }

    // 执行书籍查询操作
    private String executeBookQuery(Map<String, Object> params) {
        String query = (String) params.get("query");
        StringBuilder result = new StringBuilder();

        // 搜索书籍
        var books = bookService.searchBooks(query);

        if (books.isEmpty()) {
            // 如果没有搜索结果，尝试返回所有书籍
            books = bookService.getAllBooks();
            if (!books.isEmpty()) {
                result.append("未找到精确匹配的商品，以下是书店的所有书籍：\n\n");
            }
        }

        if (books.isEmpty()) {
            return "抱歉，书店目前没有书籍。";
        }

        result.append("共找到 ").append(books.size()).append(" 本书籍：\n\n");
        int index = 1;
        for (var book : books) {
            result.append(index++).append(". 《").append(book.getTitle()).append("》\n")
                    .append("   作者：").append(book.getAuthor()).append("\n")
                    .append("   价格：¥").append(book.getPrice()).append("\n")
                    .append("   库存：").append(book.getStock()).append(" 本\n\n");
        }

        return result.toString();
    }

    // 执行用户管理操作
    private String executeUserManagement(Map<String, Object> params) {
        try {
            Long userId = (Long) params.get("userId");

            // 获取用户信息
            var user = userService.getUserById(userId);
            return "用户 " + user.getUsername() + " 的信息已获取";
        } catch (Exception e) {
            return "获取用户信息失败: " + e.getMessage();
        }
    }
}