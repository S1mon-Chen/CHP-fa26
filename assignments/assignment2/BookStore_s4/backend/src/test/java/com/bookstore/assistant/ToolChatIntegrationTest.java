package com.bookstore.assistant;

import com.bookstore.entity.Book;
import com.bookstore.repository.BookRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ToolChatIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired BookRepository books;
    @Autowired BookTools tools;
    @MockBean ModelClient model;

    @Test void anonymousChatWithCsrfReadsCurrentDatabaseStock() throws Exception {
        Book book = new Book(); book.setTitle("实时库存测试"); book.setAuthor("测试");
        book.setIsbn("12345"); book.setStock(12); book.setPrice(BigDecimal.TEN);
        books.deleteAll(); books.saveAndFlush(book);
        assertThat(tools.check_inventory("12345").path("stock").asInt()).isEqualTo(12);
        book.setStock(7); books.saveAndFlush(book);
        ObjectNode calls = mapper.createObjectNode().put("role", "assistant").putNull("content");
        calls.putArray("tool_calls").addObject().put("id", "c1").put("type", "function")
                .putObject("function").put("name", "check_inventory")
                .put("arguments", mapper.createObjectNode().put("isbn", "12345").toString());
        ObjectNode answer = mapper.createObjectNode().put("role", "assistant").put("content", "当前库存 7 本。");
        when(model.complete(any(), any())).thenReturn(calls, answer);
        mvc.perform(post("/api/ai/tool-chat").with(csrf()).contentType("application/json")
                .content("{\"query\":\"查 12345 库存\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("当前库存 7 本。"))
                .andExpect(jsonPath("$.toolCalls[0].result.stock").value(7));
    }

    @Test void retainsCsrfProtectionAndValidatesUserInput() throws Exception {
        mvc.perform(post("/api/ai/tool-chat").contentType("application/json").content("{\"query\":\"你好\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/tool-chat").with(csrf()).contentType("application/json").content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
