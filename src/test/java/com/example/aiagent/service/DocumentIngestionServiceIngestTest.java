package com.example.aiagent.service;

import com.example.aiagent.config.RagConfig;
import com.example.aiagent.model.DocumentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceIngestTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private RagConfig ragConfig;

    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(vectorStore, jdbc, ragConfig);
    }

    @Test
    void testSearchReturnsEmptyOnVectorStoreException() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("Vector store unavailable"));

        List<Document> results = service.search("test query", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchWithoutTopKUsesConfiguredValue() {
        when(ragConfig.getTopK()).thenReturn(7);
        when(ragConfig.getSimilarityThreshold()).thenReturn(0.3);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("result")));

        List<Document> results = service.search("test");

        assertEquals(1, results.size());
        verify(vectorStore).similaritySearch(argThat((SearchRequest request) ->
                request.getTopK() == 7 && request.getSimilarityThreshold() == 0.3
        ));
    }

    @Test
    void testCountEntriesReturnsZeroWhenNoResults() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        long count = service.countEntries("deity");

        assertEquals(0, count);
    }

    @Test
    void testDeleteDocumentReturnsTrueWhenDeleted() {
        when(jdbc.update(anyString(), eq("doc-1"))).thenReturn(1);

        assertTrue(service.deleteDocument("doc-1"));
    }

    @Test
    void testDeleteDocumentReturnsFalseWhenNotFound() {
        when(jdbc.update(anyString(), eq("nonexistent"))).thenReturn(0);

        assertFalse(service.deleteDocument("nonexistent"));
    }
}
