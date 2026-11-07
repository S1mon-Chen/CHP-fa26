package com.bookstore;

import com.bookstore.service.RAGService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagResponseTest {
    @Test void parsesNestedChromaResultsWithPunctuation() {
        RAGService rag=mock(RAGService.class,CALLS_REAL_METHODS);
        String json="""
            {"metadatas":[[{"id":1,"title":"Java,进阶:指南","author":"作者","price":20.0},{"id":2,"title":"第二本","price":30}]],"distances":[[0.1,0.25]]}
            """;
        List<Map<String,Object>> results=ReflectionTestUtils.invokeMethod(rag,"parseChromaResponse",json);
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).containsEntry("id",1L).containsEntry("title","Java,进阶:指南").containsEntry("score",0.9);
        assertThat(results.get(1)).containsEntry("score",0.75);
    }
}
