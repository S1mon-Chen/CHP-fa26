package com.bookstore.agent.skill.impl;

import com.bookstore.agent.skill.Skill;
import com.bookstore.agent.skill.SkillResult;
import com.bookstore.dto.BookResponseDTO;
import com.bookstore.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 书籍搜索技能
 * 
 * 提供书籍搜索功能，可以根据关键词搜索书籍。
 * 
 * 使用示例：
 * - 输入："java"
 * - 输出：搜索结果列表
 */
@Component
public class BookSearchSkill implements Skill {

    @Autowired
    private BookService bookService;

    @Override
    public String getName() {
        return "BookSearchSkill";
    }

    @Override
    public String getDescription() {
        return "根据关键词搜索书籍，支持按标题、作者、描述进行模糊匹配";
    }

    @Override
    public String getCategory() {
        return "书籍管理";
    }

    @Override
    public boolean canExecute(Map<String, Object> context) {
        // 此技能不需要前置条件，随时可以执行
        return true;
    }

    @Override
    public SkillResult execute(String input, Map<String, Object> context) {
        try {
            List<BookResponseDTO> allBooks = bookService.getAllBooks();

            if (allBooks == null || allBooks.isEmpty()) {
                return SkillResult.success("书店目前没有书籍");
            }

            List<BookResponseDTO> filteredBooks;
            
            if (input != null && !input.trim().isEmpty()) {
                String keyword = input.toLowerCase().trim();
                filteredBooks = allBooks.stream()
                    .filter(book -> matchesKeyword(book, keyword))
                    .sorted((a, b) -> Integer.compare(countMatches(b, keyword), countMatches(a, keyword)))
                    .limit(10)
                    .collect(Collectors.toList());
            } else {
                filteredBooks = allBooks.stream().limit(10).collect(Collectors.toList());
            }

            if (filteredBooks.isEmpty()) {
                return SkillResult.success("未找到匹配的书籍，以下是书店的所有书籍：\n\n" + formatBooks(allBooks));
            }

            Map<String, Object> newContext = new HashMap<>(context != null ? context : new HashMap<>());
            newContext.put("searchKeyword", input);
            newContext.put("searchResults", filteredBooks);
            newContext.put("resultCount", filteredBooks.size());

            return SkillResult.success("共找到 " + filteredBooks.size() + " 本书籍：\n\n" + formatBooks(filteredBooks), newContext);

        } catch (Exception e) {
            return SkillResult.failure("书籍搜索失败: " + e.getMessage(), context);
        }
    }

    @Override
    public List<String> getRequiredParameters() {
        return Collections.emptyList();
    }

    private boolean matchesKeyword(BookResponseDTO book, String keyword) {
        return countMatches(book, keyword) > 0;
    }

    private int countMatches(BookResponseDTO book, String keyword) {
        int count = 0;
        String title = book.getTitle() != null ? book.getTitle().toLowerCase() : "";
        String author = book.getAuthor() != null ? book.getAuthor().toLowerCase() : "";
        String description = book.getDescription() != null ? book.getDescription().toLowerCase() : "";

        if (title.contains(keyword)) count += 3;
        if (author.contains(keyword)) count += 2;
        if (description.contains(keyword)) count += 1;

        return count;
    }

    private String formatBooks(List<BookResponseDTO> books) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (BookResponseDTO book : books) {
            sb.append(index++).append(". 《").append(book.getTitle()).append("》\n")
              .append("   作者：").append(book.getAuthor()).append("\n")
              .append("   价格：¥").append(book.getPrice()).append("\n")
              .append("   库存：").append(book.getStock()).append(" 本\n\n");
        }
        return sb.toString();
    }
}