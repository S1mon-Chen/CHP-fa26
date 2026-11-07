package com.bookstore.shopping;

import com.bookstore.assistant.ModelClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShoppingIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean ModelClient model;

    @Test void anonymousShoppingIsStatelessAndValidatesInputs() throws Exception {
        when(model.complete(any(), any())).thenReturn(mapper.createObjectNode().put("role", "assistant").put("content", "想了解什么主题的图书？"));
        mvc.perform(post("/api/ai/shopping/chat").contentType("application/json").content("{\"query\":\"你好\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modelRequests").value(1)).andExpect(jsonPath("$.steps").isEmpty());
        mvc.perform(post("/api/ai/shopping/chat").contentType("application/json").content("{\"query\":\"你好\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ai/shopping/chat").contentType("application/json").content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/shopping/policy-search").contentType("application/json").content("{\"query\":\"退货\",\"topK\":99}"))
                .andExpect(status().isBadRequest());
    }
}
