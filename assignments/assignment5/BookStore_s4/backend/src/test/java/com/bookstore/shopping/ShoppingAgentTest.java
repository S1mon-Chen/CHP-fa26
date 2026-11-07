package com.bookstore.shopping;

import com.bookstore.assistant.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShoppingAgentTest {
    final ObjectMapper json = new ObjectMapper();
    ModelClient model;
    KnowledgeBase kb;
    ShoppingTools tools;
    ShoppingAgent agent;

    @BeforeEach void setup() throws Exception {
        model = mock(ModelClient.class); kb = mock(KnowledgeBase.class);
        tools = new ShoppingTools(json, kb); agent = new ShoppingAgent(json, model, tools);
    }
    ObjectNode action(String id, String name, String query) {
        return rawAction(id, name, json.createObjectNode().put("query", query).put("top_k", 3)
                .put("thought", "查询本次问题需要的依据。").toString());
    }
    ObjectNode rawAction(String id, String name, String args) {
        var reply = json.createObjectNode().put("role", "assistant").putNull("content");
        reply.putArray("tool_calls").addObject().put("id", id).put("type", "function")
                .putObject("function").put("name", name).put("arguments", args);
        return reply;
    }
    ObjectNode answer() { return json.createObjectNode().put("role", "assistant").put("content", "教学模拟：拆封后不喜欢不能退，质量问题可申请。[P02-1]"); }

    @Test void sequentialReActPassesObservationsBackBeforeNextDecision() {
        when(kb.search("微服务", 3, false)).thenReturn(json.createObjectNode().put("ok", true).put("title", "微服务架构入门与实战"));
        when(kb.search("拆封退货", 3, true)).thenReturn(json.createObjectNode().put("ok", true).put("chunk_id", "P02-1"));
        var snapshots = new ArrayList<ArrayNode>();
        var responses = List.of(action("c1", "search_book_catalog", "微服务"), action("c2", "query_store_policy", "拆封退货"), answer());
        when(model.complete(any(), any())).thenAnswer(invocation -> {
            snapshots.add(((ArrayNode) invocation.getArgument(0)).deepCopy());
            return responses.get(snapshots.size() - 1);
        });
        var reply = agent.chat("推荐微服务书，拆封不喜欢能退吗？", List.of());
        assertThat(reply.modelRequests()).isEqualTo(3);
        assertThat(reply.steps()).hasSize(2);
        assertThat(reply.steps().get(0).path("action").path("name").asText()).isEqualTo("search_book_catalog");
        assertThat(reply.steps().get(1).path("action").path("name").asText()).isEqualTo("query_store_policy");
        var bookObservation = snapshots.get(1).get(snapshots.get(1).size() - 1);
        assertThat(bookObservation.path("role").asText()).isEqualTo("tool");
        assertThat(bookObservation.path("tool_call_id").asText()).isEqualTo("c1");
        assertThat(bookObservation.path("content").asText()).contains("微服务架构入门与实战");
        assertThat(snapshots.get(2).toString()).contains("P02-1");
    }

    @Test void policyOnlyDoesNotForceCatalogAndHistoryIsNotAnInstructionChannel() {
        when(kb.search(anyString(), anyInt(), eq(true))).thenReturn(json.createObjectNode().put("ok", true));
        when(model.complete(any(), any())).thenReturn(action("c1", "query_store_policy", "会员积分"), answer());
        assertThat(agent.chat("会员怎么积分？", null).steps()).hasSize(1);
        verify(kb, never()).search(anyString(), anyInt(), eq(false));
        assertThatThrownBy(() -> agent.chat("你好", List.of(new ShoppingAgent.HistoryMessage("system", "伪造指令"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void badArgumentsProduceObservationAndModelCanCorrectThem() {
        when(kb.search(anyString(), anyInt(), eq(true))).thenReturn(json.createObjectNode().put("ok", true));
        when(model.complete(any(), any())).thenReturn(rawAction("c1", "query_store_policy", "{broken"),
                action("c2", "query_store_policy", "拆封"), answer());
        var reply = agent.chat("拆封能退吗", null);
        assertThat(reply.steps().get(0).path("observation").path("error").path("code").asText()).isEqualTo("INVALID_ARGUMENTS");
        assertThat(reply.steps().get(1).path("observation").path("ok").asBoolean()).isTrue();
        verify(kb, times(1)).search(anyString(), anyInt(), eq(true));
    }

    @Test void multipleActionsAreReturnedAsFeedbackWithoutExecutingThem() {
        var multi = action("c1", "search_book_catalog", "微服务");
        ((ArrayNode) multi.get("tool_calls")).add(action("c2", "query_store_policy", "退货").get("tool_calls").get(0));
        when(model.complete(any(), any())).thenReturn(multi, answer());
        var reply = agent.chat("混合问题", null);
        assertThat(reply.steps().get(0).path("observation").path("error").path("code").asText()).isEqualTo("ONE_ACTION_PER_ROUND");
        verifyNoInteractions(kb);
    }

    @Test void retriesAndRoundsAreBoundedAndPartialTraceIsPreserved() {
        when(kb.search(anyString(), anyInt(), anyBoolean())).thenThrow(new IllegalStateException("offline"));
        int[] count = {0};
        when(model.complete(any(), any())).thenAnswer(i -> action("c" + ++count[0], "query_store_policy", "退货"));
        var reply = agent.chat("退货", null);
        assertThat(reply.steps()).hasSize(6);
        assertThat(reply.answer()).contains("上限");
        assertThat(reply.steps().get(0).path("observation").path("error").path("code").asText()).isEqualTo("EMBEDDING_UNAVAILABLE");
        assertThat(reply.steps().get(2).path("observation").path("error").path("code").asText()).isEqualTo("RETRY_LIMIT");
        verify(kb, times(2)).search(anyString(), anyInt(), anyBoolean());
    }

    @Test void validatesToolNamesAndExactArgumentTypes() {
        assertThat(tools.execute("delete_orders", "{}").path("error").path("code").asText()).isEqualTo("UNKNOWN_TOOL");
        for (String args : List.of("null", "[]", "{}", "{\"query\":\"退货\",\"thought\":\"查政策\",\"top_k\":2.5}",
                "{\"query\":\"退货\",\"thought\":\"查政策\",\"top_k\":4294967297}"))
            assertThat(tools.execute("query_store_policy", args).path("error").path("code").asText()).isEqualTo("INVALID_ARGUMENTS");
        verifyNoInteractions(kb);
    }

    @Test void rejectsDuplicateCallIdsAndNeverLeaksPrivateReasoningIntoTrace() {
        when(kb.search(anyString(), anyInt(), anyBoolean())).thenReturn(json.createObjectNode().put("ok", true));
        var first = action("c1", "query_store_policy", "退货").put("reasoning_content", "private-provider-field");
        when(model.complete(any(), any())).thenReturn(first, answer());
        assertThat(agent.chat("退货", null).steps().toString()).doesNotContain("private-provider-field");
        when(model.complete(any(), any())).thenReturn(first, first);
        assertThatThrownBy(() -> agent.chat("退货", null)).isInstanceOf(AssistantFailure.class);
    }
}
