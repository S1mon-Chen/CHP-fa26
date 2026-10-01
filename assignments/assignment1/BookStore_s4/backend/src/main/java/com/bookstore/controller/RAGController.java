package com.bookstore.controller;

import com.bookstore.service.RAGService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RAGController {

    @Autowired
    private RAGService ragService;

    @PostMapping("/search")
    public List<Map<String, Object>> searchBooks(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int k = request.get("k") != null ? (int) request.get("k") : 5;
        return ragService.searchBooks(query, k);
    }

    @PostMapping("/update")
    public Map<String, String> updateBookVectors() {
        ragService.updateBookVectors();
        return Map.of("message", "书籍向量更新成功");
    }
}