package com.bookstore.controller;

import com.bookstore.functioncalling.FunctionCallingDTOs.ChatRequest;
import com.bookstore.functioncalling.FunctionCallingDTOs.ChatResponse;
import com.bookstore.functioncalling.FunctionCallingService;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 客服助手 Function Calling 控制器
 */
@RestController
@RequestMapping("/api/ai")
public class FunctionCallingController {

    @Autowired
    private FunctionCallingService functionCallingService;

    /**
     * 与支持函数调用的客服助手对话
     * @param request 用户问题和历史消息
     * @return 最终回答 + 本次触发的函数调用记录
     */
    @PostMapping("/function-chat")
    public ChatResponse functionChat(@RequestBody ChatRequest request) {
        return functionCallingService.chat(request.query(), request.history());
    }

    /**
     * 查看提供给大模型的 Tool 列表（Function Schema）
     * @return Tool 列表
     */
    @GetMapping("/tools")
    public ArrayNode tools() {
        return functionCallingService.getTools();
    }
}
