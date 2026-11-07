package com.bookstore.assistant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class ToolChatController {
    private final ToolChatService service;
    public ToolChatController(ToolChatService service) { this.service = service; }

    public record ChatRequest(String query, List<ToolChatService.HistoryMessage> history) {}

    @PostMapping("/tool-chat")
    public ToolChatService.ChatReply chat(@RequestBody ChatRequest request) {
        return service.chat(request.query(), request.history());
    }

    @ExceptionHandler(AssistantFailure.class)
    public ResponseEntity<Map<String, String>> handleFailure(AssistantFailure failure) {
        return ResponseEntity.status(failure.status()).body(Map.of("message", failure.getMessage()));
    }
}
