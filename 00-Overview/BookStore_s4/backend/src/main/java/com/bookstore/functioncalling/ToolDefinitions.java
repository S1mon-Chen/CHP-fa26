package com.bookstore.functioncalling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * 提供给大模型的 Tool 列表（OpenAI / DeepSeek Function Calling 格式）
 * 每个函数包含：名称、用途描述、参数的 JSON Schema（类型、描述、是否必填）
 */
public final class ToolDefinitions {

    public static final String CHECK_INVENTORY = "check_inventory";
    public static final String GET_COMPETITOR_PRICE = "get_competitor_price";

    private static final String TOOLS_JSON = """
        [
          {
            "type": "function",
            "function": {
              "name": "check_inventory",
              "description": "查询本书店某本书的实时库存。返回书名、作者、本店售价、库存数量和库存状态。用户询问某本书还有没有货、还剩多少本时调用。",
              "parameters": {
                "type": "object",
                "properties": {
                  "isbn": {
                    "type": "string",
                    "description": "书籍的 ISBN，只包含数字（ISBN-10 末位可为 X），可带连字符，例如 \\"9787111544937\\"、\\"7-111-54493-X\\"。书店内部演示数据也使用 5 位编号，例如 \\"12345\\"。"
                  }
                },
                "required": ["isbn"],
                "additionalProperties": false
              }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "get_competitor_price",
              "description": "调用外部比价接口，查询竞争对手（京东、当当、淘宝）对同一本书的售价，并与本店价格对比。用户询问别家卖多少钱、哪里更便宜时调用。该外部接口偶尔会超时。",
              "parameters": {
                "type": "object",
                "properties": {
                  "isbn": {
                    "type": "string",
                    "description": "书籍的 ISBN，格式要求同 check_inventory。"
                  }
                },
                "required": ["isbn"],
                "additionalProperties": false
              }
            }
          }
        ]
        """;

    private ToolDefinitions() {
    }

    public static ArrayNode tools(ObjectMapper mapper) {
        try {
            return (ArrayNode) mapper.readTree(TOOLS_JSON);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Tool 定义 JSON 不合法", e);
        }
    }
}
