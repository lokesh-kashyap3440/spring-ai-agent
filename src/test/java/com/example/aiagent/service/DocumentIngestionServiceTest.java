package com.example.aiagent.service;

import com.example.aiagent.config.RagConfig;
import com.example.aiagent.model.DocumentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.multipart.MultipartFile;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private RagConfig ragConfig;

    @Mock
    private MultipartFile file;

    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(vectorStore, jdbc, ragConfig);
    }

    @Test
    void testListDocumentsEmpty() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());
        List<DocumentInfo> docs = service.listDocuments();
        assertTrue(docs.isEmpty());
    }

    @Test
    void testListDocumentsReturnsList() throws Exception {
        Timestamp now = Timestamp.from(Instant.now());
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("doc_id")).thenReturn("1");
        when(rs.getString("filename")).thenReturn("test.pdf");
        when(rs.getString("content_type")).thenReturn("application/pdf");
        when(rs.getLong("size")).thenReturn(100L);
        when(rs.getInt("chunks")).thenReturn(2);
        when(rs.getTimestamp("uploaded_at")).thenReturn(now);

        when(jdbc.query(anyString(), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<DocumentInfo> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<DocumentInfo> docs = service.listDocuments();
        assertEquals(1, docs.size());
        assertEquals("1", docs.get(0).getId());
        assertEquals("test.pdf", docs.get(0).getFilename());
    }

    @Test
    void testDeleteDocumentFound() {
        when(jdbc.update(anyString(), eq("1"))).thenReturn(1);
        boolean removed = service.deleteDocument("1");
        assertTrue(removed);
    }

    @Test
    void testDeleteDocumentNotFound() {
        when(jdbc.update(anyString(), eq("nonexistent"))).thenReturn(0);
        boolean removed = service.deleteDocument("nonexistent");
        assertFalse(removed);
    }

    @Test
    void testSearchWithoutTopK() {
        when(ragConfig.getTopK()).thenReturn(3);
        when(ragConfig.getSimilarityThreshold()).thenReturn(0.5);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("result")));
        List<Document> results = service.search("test query");
        assertEquals(1, results.size());
        assertEquals("result", results.get(0).getText());
    }

    @Test
    void testSearchWithTopK() {
        when(ragConfig.getSimilarityThreshold()).thenReturn(0.5);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("a"), new Document("b")));
        List<Document> results = service.search("test", 2);
        assertEquals(2, results.size());
    }

    @Test
    void testSearchReturnsEmpty() {
        when(ragConfig.getTopK()).thenReturn(3);
        when(ragConfig.getSimilarityThreshold()).thenReturn(0.5);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());
        List<Document> results = service.search("nothing");
        assertTrue(results.isEmpty());
    }
}
