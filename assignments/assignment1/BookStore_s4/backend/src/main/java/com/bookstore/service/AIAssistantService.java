package com.bookstore.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AIAssistantService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "llama3.2:latest";

    @Autowired
    private MCPService mcpService;

    @Autowired
    private BookService bookService;

    @Autowired
    private RAGService ragService;

    public String handleRequest(String userQuery, Long userId) {
        try {
            // 第一步：调用Ollama识别意图并决定调用哪个MCP服务
            String intentResult = analyzeIntentAndPlan(userQuery, userId);

            System.out.println("Ollama意图分析结果: " + intentResult);

            // 第二步：解析Ollama的响应，决定是否需要调用MCP服务
            String mcpResult = executeMCPIfNeeded(intentResult, userQuery, userId);

            // 第三步：将MCP结果再次发送给Ollama生成最终答案
            String finalAnswer = generateFinalAnswer(userQuery, mcpResult, userId);

            return finalAnswer;

        } catch (Exception e) {
            e.printStackTrace();
            return "抱歉，处理您的请求时出现了问题：" + e.getMessage() + "。请稍后再试。";
        }
    }

    // 第一步：调用Ollama分析用户意图和规划行动
    private String analyzeIntentAndPlan(String userQuery, Long userId) {
        String systemPrompt = "你是一个智能助手的大脑，负责分析用户意图并决定是否需要调用工具来获取真实信息。\n\n" +
                "重要：你的任务是判断用户的问题是否需要从数据库或服务中获取真实信息来回答。\n\n" +
                "可用的工具(MCP服务)：\n" +
                "1. book_query - 当用户询问书店有什么书、书名、作者、书籍列表、搜索书籍、查找书籍时使用\n" +
                "2. book_recommendation - 当用户请求推荐书籍、要什么书、买什么书时使用\n" +
                "3. order_management - 当用户询问订单、购买记录、下单、我的订单时使用\n" +
                "4. sales_statistics - 当用户查看销售数据、统计信息、销量时使用\n" +
                "5. user_management - 当用户查询个人信息、用户资料、我的信息时使用\n" +
                "6. promotion_info - 当用户询问促销活动、优惠信息、打折、优惠券时使用\n" +
                "7. direct_answer - 当用户的问题是一般性对话、问候、闲聊时使用，不需要获取真实数据\n\n" +
                "判断规则：\n" +
                "- 如果用户询问具体的信息（如书名、价格、订单等），必须使用工具\n" +
                "- 如果用户只是打招呼或闲聊，可以使用direct_answer\n" +
                "- 如果用户问\"有什么书\"、\"卖什么书\"、\"我要买书\"等，必须使用book_query\n\n" +
                "输出格式：\n" +
                "只输出工具名称，不要输出其他内容。\n\n" +
                "用户问题：" + userQuery;

        if (userId != null) {
            systemPrompt += "\n\n当前用户已登录，用户ID：" + userId;
        } else {
            systemPrompt += "\n\n当前用户未登录";
        }

        return callOllama(systemPrompt);
    }

    // 第二步：根据意图分析结果决定是否调用MCP服务
    private String executeMCPIfNeeded(String intent, String userQuery, Long userId) {
        String intentLower = intent.toLowerCase().trim();

        System.out.println("识别的意图: " + intentLower);

        // 如果Ollama返回的是工具名称，调用相应的MCP服务
        if (intentLower.contains("book_query")) {
            System.out.println("调用书籍查询MCP服务...");
            String query = extractBookQuery(userQuery);
            return mcpService.executeAction("book_query",
                java.util.Map.of("query", query));
        }
        else if (intentLower.contains("book_recommendation")) {
            System.out.println("调用书籍推荐MCP服务...");
            // 使用RAG服务进行智能推荐
            var recommendedBooks = ragService.searchBooks(userQuery, 5);
            if (!recommendedBooks.isEmpty()) {
                StringBuilder bookList = new StringBuilder();
                bookList.append("基于您的兴趣，为您推荐以下书籍：\n\n");
                int index = 1;
                for (var book : recommendedBooks) {
                    bookList.append(index++).append(". 《").append(book.get("title")).append("》\n")
                            .append("   作者：").append(book.get("author")).append("\n")
                            .append("   价格：¥").append(book.get("price")).append("\n")
                            .append("   相关性：").append(book.get("score")).append("\n\n");
                }
                return bookList.toString();
            } else {
                // 回退到MCP服务
                var allBooks = bookService.getAllBooks();
                StringBuilder bookList = new StringBuilder();
                for (var book : allBooks) {
                    bookList.append(book.getTitle())
                            .append(" - 作者: ").append(book.getAuthor())
                            .append(", 价格: ¥").append(book.getPrice())
                            .append("\n");
                }
                return "以下是书店的所有书籍列表：\n" + bookList.toString();
            }
        }
        else if (intentLower.contains("order_management")) {
            System.out.println("调用订单管理MCP服务...");
            if (userId == null) {
                return "ERROR:USER_NOT_LOGGED_IN";
            }
            return mcpService.executeAction("order_management",
                java.util.Map.of("userId", userId, "query", userQuery));
        }
        else if (intentLower.contains("sales_statistics")) {
            System.out.println("调用销售统计MCP服务...");
            return mcpService.executeAction("sales_statistics",
                java.util.Map.of("query", userQuery));
        }
        else if (intentLower.contains("user_management")) {
            System.out.println("调用用户管理MCP服务...");
            if (userId == null) {
                return "ERROR:USER_NOT_LOGGED_IN";
            }
            return mcpService.executeAction("user_management",
                java.util.Map.of("userId", userId, "query", userQuery));
        }
        else if (intentLower.contains("promotion_info")) {
            System.out.println("调用促销信息MCP服务...");
            return "促销活动信息：\n1. 全场图书8折优惠，截止日期：2026年5月31日\n2. 满100减20，满200减50\n3. 新用户注册送50元优惠券";
        }
        else {
            // direct_answer或其他情况，返回空表示不需要MCP服务
            System.out.println("不需要调用MCP服务，直接回答...");
            return "NO_MCP_NEEDED";
        }
    }

    // 从用户查询中提取书籍查询关键词
    private String extractBookQuery(String userQuery) {
        // 如果用户问"有什么书"、"卖什么书"等，返回空字符串表示查询所有书籍
        if (userQuery.contains("什么书") || userQuery.contains("都有") ||
            userQuery.contains("哪些") || userQuery.contains("卖")) {
            return "";
        }
        // 否则返回原始查询
        return userQuery;
    }

    // 第三步：将MCP结果再次发送给Ollama生成最终答案
    private String generateFinalAnswer(String userQuery, String mcpResult, Long userId) {
        String prompt;

        if ("NO_MCP_NEEDED".equals(mcpResult)) {
            // 不需要MCP服务，直接回答
            prompt = "你是一个智能书店助手，请用友好、专业的语气直接回答用户的问题。\n\n" +
                    "用户问题：" + userQuery + "\n\n" +
                    "请直接回答用户的问题，不需要调用任何工具。";
        }
        else if ("ERROR:USER_NOT_LOGGED_IN".equals(mcpResult)) {
            // 用户未登录
            prompt = "用户询问了一些需要登录后才能操作的功能，但用户目前未登录。\n\n" +
                    "用户问题：" + userQuery + "\n\n" +
                    "请礼貌地引导用户先登录或注册账户，说明登录后可以享受的服务。";
        }
        else {
            // 有MCP服务的结果，将其作为上下文
            prompt = "你是一个智能书店助手。请根据以下从工具获取的真实信息，用友好、专业的方式回答用户的问题。\n\n" +
                    "从工具获取的信息：\n" + mcpResult + "\n\n" +
                    "用户原始问题：" + userQuery + "\n\n" +
                    "请根据工具提供的信息，准确、详细地回答用户的问题。如果信息中包含书籍列表，请详细介绍每一本书。";
        }

        return callOllama(prompt);
    }

    // 调用Ollama API
    private String callOllama(String prompt) {
        try {
            URL url = new URL(OLLAMA_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("model", MODEL);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            requestBody.put("options", java.util.Map.of(
                "temperature", 0.7,
                "top_p", 0.9,
                "num_predict", 500
            ));

            ObjectMapper mapper = new ObjectMapper();
            String jsonInputString = mapper.writeValueAsString(requestBody);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> result = mapper.readValue(response.toString(), java.util.Map.class);

            String aiResponse = (String) result.get("response");
            return aiResponse != null ? aiResponse.trim() : "";

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("调用Ollama失败: " + e.getMessage(), e);
        }
    }
}