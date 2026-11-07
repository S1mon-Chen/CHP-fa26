package com.bookstore.shopping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Set;

@Component
public class ShoppingTools {
    private final ObjectMapper mapper;
    private final KnowledgeBase knowledge;

    public ShoppingTools(ObjectMapper mapper, KnowledgeBase knowledge) {
        this.mapper = mapper;
        this.knowledge = knowledge;
    }

    public ObjectNode execute(String name, String arguments) {
        if (!Set.of("search_book_catalog", "query_store_policy").contains(name))
            return error("UNKNOWN_TOOL", "只允许调用提供的两个工具。", false);
        try {
            var args = mapper.readTree(arguments);
            if (args == null || !args.isObject() || args.size() != 3
                    || !args.path("query").isTextual() || args.path("query").asText().isBlank()
                    || args.path("query").asText().length() > 500
                    || !args.path("thought").isTextual() || args.path("thought").asText().isBlank()
                    || args.path("thought").asText().length() > 160
                    || !args.path("top_k").isIntegralNumber() || !args.path("top_k").canConvertToInt()
                    || args.path("top_k").asInt() < 1 || args.path("top_k").asInt() > 5)
                return error("INVALID_ARGUMENTS", "需要 query（1..500字）、thought（1..160字行动摘要）、top_k（1..5整数）。", true);
            return knowledge.search(args.path("query").asText(), args.path("top_k").asInt(),
                    name.equals("query_store_policy"));
        } catch (IOException e) {
            return error("INVALID_ARGUMENTS", "参数必须是合法 JSON 对象。", true);
        } catch (IllegalStateException e) {
            return error("EMBEDDING_UNAVAILABLE", "本地向量服务不可用，请检查 8092 端口；可重试一次，仍失败请说明缺少依据。", true);
        }
    }

    public ObjectNode error(String code, String message, boolean retryable) {
        var node = mapper.createObjectNode().put("ok", false);
        node.putObject("error").put("code", code).put("message", message).put("retryable", retryable);
        return node;
    }
}
