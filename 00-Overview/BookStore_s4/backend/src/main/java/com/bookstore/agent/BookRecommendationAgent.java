package com.bookstore.agent;

import com.bookstore.dto.BookResponseDTO;
import com.bookstore.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图书推荐智能体
 * 负责根据用户意图进行书籍推荐和搜索
 */
@Component
public class BookRecommendationAgent extends AbstractAgent {

    @Autowired
    private BookService bookService;

    @Override
    public boolean canHandle(String intent) {
        if (intent == null) {
            return false;
        }
        String intentLower = intent.toLowerCase();
        return intentLower.contains("推荐") ||
               intentLower.contains("找书") ||
               intentLower.contains("想买") ||
               intentLower.contains("搜索") ||
               intentLower.contains("什么书") ||
               intentLower.contains("书籍");
    }

    @Override
    protected AgentResponse doExecute(String input, Map<String, Object> context) {
        try {
            List<String> keywords = extractKeywords(input);
            List<BookResponseDTO> allBooks = bookService.getAllBooks();

            if (allBooks == null || allBooks.isEmpty()) {
                return AgentResponse.success("抱歉，书店目前没有可推荐的书籍。", context);
            }

            List<BookResponseDTO> filteredBooks;
            if (!keywords.isEmpty()) {
                filteredBooks = allBooks.stream()
                    .filter(book -> matchesKeywords(book, keywords))
                    .sorted((a, b) -> Integer.compare(countMatches(b, keywords), countMatches(a, keywords)))
                    .limit(5)
                    .collect(Collectors.toList());
            } else {
                filteredBooks = allBooks.stream().limit(5).collect(Collectors.toList());
            }

            if (filteredBooks.isEmpty()) {
                filteredBooks = allBooks.stream().limit(5).collect(Collectors.toList());
            }

            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("根据您的需求，为您推荐以下书籍：\n\n");

            int index = 1;
            for (BookResponseDTO book : filteredBooks) {
                resultBuilder.append(index++).append(". 《").append(book.getTitle()).append("》\n")
                        .append("   作者：").append(book.getAuthor()).append("\n")
                        .append("   价格：¥").append(book.getPrice()).append("\n\n");
            }

            Map<String, Object> selectedBookMap = convertToMap(filteredBooks.get(0));

            Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
            newContext.put("recommendedBooks", filteredBooks);
            newContext.put("selectedBook", selectedBookMap);

            return AgentResponse.success(resultBuilder.toString(), newContext, "OrderProcessingAgent");

        } catch (Exception e) {
            logError("图书推荐智能体执行异常: {}", e.getMessage());
            return AgentResponse.failure("书籍推荐服务暂时不可用，请稍后再试。", context);
        }
    }

    private boolean matchesKeywords(BookResponseDTO book, List<String> keywords) {
        return countMatches(book, keywords) > 0;
    }

    private int countMatches(BookResponseDTO book, List<String> keywords) {
        int count = 0;
        String title = book.getTitle() != null ? book.getTitle().toLowerCase() : "";
        String author = book.getAuthor() != null ? book.getAuthor().toLowerCase() : "";
        String description = book.getDescription() != null ? book.getDescription().toLowerCase() : "";

        for (String keyword : keywords) {
            if (title.contains(keyword)) {
                count += 3;
            }
            if (author.contains(keyword)) {
                count += 2;
            }
            if (description.contains(keyword)) {
                count += 1;
            }
        }
        return count;
    }

    private List<String> extractKeywords(String query) {
        List<String> keywords = new ArrayList<>();
        String lowerQuery = query.toLowerCase();

        String[] techKeywords = {"java", "python", "javascript", "js", "c++", "c语言", "golang", "go", "rust", "ruby", "php", "swift", "kotlin", "scala", "typescript", "react", "vue", "angular", "node", "spring", "django", "flask", "springboot", "mybatis", "hibernate", "reactnative", "flutter", "android", "ios", "macos", "windows", "linux", "unix", "docker", "kubernetes", "k8s", "git", "github", "gitlab", "maven", "gradle", "npm", "yarn", "webpack", "vite", "html", "css", "sass", "less", "sql", "mysql", "postgresql", "oracle", "mongodb", "redis", "elasticsearch", "kafka", "rabbitmq", "nginx", "apache", "tomcat", "jetty", "aws", "azure", "gcp", "devops", "ci/cd", "agile", "scrum", "tdd", "bdd", "微服务", "分布式", "云计算", "大数据", "机器学习", "深度学习", "人工智能", "ai", "ml", "dl", "神经网络", "算法", "数据结构", "数据库", "网络", "安全", "加密", "区块链", "web3"};

        for (String keyword : techKeywords) {
            if (lowerQuery.contains(keyword)) {
                keywords.add(keyword);
            }
        }

        return keywords;
    }

    private Map<String, Object> convertToMap(BookResponseDTO book) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", book.getId());
        map.put("title", book.getTitle());
        map.put("author", book.getAuthor());
        map.put("price", book.getPrice());
        map.put("description", book.getDescription());
        map.put("isbn", book.getIsbn());
        map.put("stock", book.getStock());
        map.put("coverImage", book.getCoverImage());
        return map;
    }
}