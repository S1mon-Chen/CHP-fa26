package com.bookstore.shopping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeBaseTest {
    @Test void chunkWindowsPreserveSourceAndOverlapWithoutCrossingSections() {
        String document = "# 标题\n## P01 退货\n" + "退货条件。".repeat(150) + "\n\n## P02 会员\n免费注册会员。\n";
        var chunks = KnowledgeBase.split(document, 100, 20);
        assertThat(chunks).hasSizeGreaterThan(7);
        StringBuilder reconstructed = new StringBuilder();
        int previousEnd = -1;
        for (var c : chunks) {
            assertThat(c.text()).isEqualTo(document.substring(c.start(), c.end()));
            assertThat(c.text().length()).isLessThanOrEqualTo(100);
            assertThat(c.text()).doesNotContain("##");
            if (c.id().startsWith("P01")) {
                if (previousEnd >= 0) assertThat(c.start()).isEqualTo(previousEnd - 20);
                reconstructed.append(c.text().substring(previousEnd < 0 ? 0 : 20));
                previousEnd = c.end();
            }
        }
        assertThat(reconstructed.toString()).isEqualTo("退货条件。".repeat(150));
        assertThat(chunks.get(chunks.size() - 1).id()).isEqualTo("P02-1");
        assertThatThrownBy(() -> KnowledgeBase.split(document, 100, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void cosineUsesDirectionAndRejectsIncompatibleDimensions() {
        assertThat(KnowledgeBase.cosine(new double[]{2, 0}, new double[]{5, 0})).isEqualTo(1);
        assertThat(KnowledgeBase.cosine(new double[]{2, 0}, new double[]{0, 5})).isZero();
        assertThat(KnowledgeBase.cosine(new double[]{0, 0}, new double[]{1, 2})).isZero();
        assertThatThrownBy(() -> KnowledgeBase.cosine(new double[]{1}, new double[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void indexLoadsAllPoliciesAndRetrievesSourceEvidenceInScoreOrder() throws Exception {
        EmbeddingClient client = mock(EmbeddingClient.class);
        when(client.embed(anyList(), eq("passage"))).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            double[][] vectors = new double[texts.size()][512];
            for (int i = 0; i < texts.size(); i++) vectors[i][texts.get(i).startsWith("P02 ") ? 0 : 1] = 1;
            return vectors;
        });
        double[][] query = new double[1][512]; query[0][0] = 1;
        when(client.embed(anyList(), eq("query"))).thenReturn(query);
        var kb = new KnowledgeBase(new ObjectMapper(), client);
        var result = kb.search("拆封后不喜欢", 3, true);
        assertThat(result.path("matches")).hasSize(1);
        assertThat(result.path("matches").get(0).path("chunk_id").asText()).isEqualTo("P02-1");
        assertThat(result.path("matches").get(0).path("text").asText()).contains("不支持", "质量问题", "P03");
        var manifest = kb.manifest();
        assertThat(manifest.path("chunk_count").asInt()).isEqualTo(7);
        assertThat(manifest.path("source_sha256").asText()).hasSize(64);
        assertThat(manifest.path("chunks").get(0).path("embedding")).hasSize(512);
        kb.search("拆封", 1, true);
        verify(client, times(1)).embed(anyList(), eq("passage"));
        query[0][0] = 0; query[0][2] = 1;
        assertThat(kb.search("无关问题", 3, true).path("found").asBoolean()).isFalse();
    }

    @Test void failedBuildDoesNotCachePartialIndexAndBadRequestsFailBeforeEmbedding() throws Exception {
        var client = mock(EmbeddingClient.class);
        when(client.embed(anyList(), eq("passage"))).thenThrow(new IllegalStateException("offline"));
        var kb = new KnowledgeBase(new ObjectMapper(), client);
        assertThatThrownBy(() -> kb.search(" ", 3, true)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
        for (int i = 0; i < 2; i++)
            assertThatThrownBy(() -> kb.search("退货", 3, true)).isInstanceOf(IllegalStateException.class);
        verify(client, times(2)).embed(anyList(), eq("passage"));
    }
}
