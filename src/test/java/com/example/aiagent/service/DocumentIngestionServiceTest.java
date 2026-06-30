package com.example.aiagent.service;

import com.example.aiagent.model.DocumentInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private MultipartFile file;

    private DocumentIngestionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new DocumentIngestionService(vectorStore, redisTemplate, objectMapper);
    }

    @Test
    void testListDocumentsEmpty() {
        when(valueOps.get("rag:documents")).thenReturn(null);

        List<DocumentInfo> docs = service.listDocuments();

        assertTrue(docs.isEmpty());
    }

    @Test
    void testListDocumentsReturnsList() throws Exception {
        String json = """
                [{"id":"1","filename":"test.pdf","contentType":"application/pdf","size":100,"chunks":2}]
                """;
        when(valueOps.get("rag:documents")).thenReturn(json);

        List<DocumentInfo> docs = service.listDocuments();

        assertEquals(1, docs.size());
        assertEquals("1", docs.get(0).getId());
        assertEquals("test.pdf", docs.get(0).getFilename());
    }

    @Test
    void testListDocumentsHandlesInvalidJson() {
        when(valueOps.get("rag:documents")).thenReturn("invalid json");

        List<DocumentInfo> docs = service.listDocuments();

        assertTrue(docs.isEmpty());
    }

    @Test
    void testDeleteDocumentFound() throws Exception {
        String json = """
                [{"id":"1","filename":"a.pdf","contentType":"application/pdf","size":100,"chunks":1}]
                """;
        when(valueOps.get("rag:documents")).thenReturn(json);

        boolean removed = service.deleteDocument("1");

        assertTrue(removed);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("rag:documents"), captor.capture());
        assertFalse(captor.getValue().contains("\"id\":\"1\""));
    }

    @Test
    void testDeleteDocumentNotFound() {
        when(valueOps.get("rag:documents")).thenReturn("[]");

        boolean removed = service.deleteDocument("nonexistent");

        assertFalse(removed);
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    void testSearchWithoutTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("result")));

        List<Document> results = service.search("test query");

        assertEquals(1, results.size());
        assertEquals("result", results.get(0).getText());
    }

    @Test
    void testSearchWithTopK() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("a"), new Document("b")));

        List<Document> results = service.search("test", 2);

        assertEquals(2, results.size());
    }

    @Test
    void testSearchReturnsEmpty() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        List<Document> results = service.search("nothing");

        assertTrue(results.isEmpty());
    }
}
