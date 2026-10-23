package com.bookstore.shopping;

import com.bookstore.assistant.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/ai/shopping")
public class ShoppingController {
    private final ShoppingAgent agent;
    private final KnowledgeBase knowledge;
    public ShoppingController(ShoppingAgent agent, KnowledgeBase knowledge) { this.agent = agent; this.knowledge = knowledge; }
    public record ChatRequest(String query, List<ShoppingAgent.HistoryMessage> history) {}
    public record SearchRequest(String query, int topK) {}

    @PostMapping("/chat") public ShoppingAgent.Reply chat(@RequestBody ChatRequest request) {
        return agent.chat(request.query(), request.history());
    }
    @PostMapping("/policy-search") public ObjectNode search(@RequestBody SearchRequest request) {
        return knowledge.search(request.query(), request.topK(), true);
    }
    @GetMapping("/index") public ObjectNode index() { return knowledge.manifest(); }

    @ExceptionHandler(AssistantFailure.class)
    public ResponseEntity<Map<String, String>> failure(AssistantFailure error) {
        return ResponseEntity.status(error.status()).body(Map.of("message", error.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> embeddingFailure(IllegalStateException error) {
        return ResponseEntity.status(503).body(Map.of("message", "本地 Embedding 服务不可用，请先运行 scripts/embedding_server.py。"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badInput(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }
}
