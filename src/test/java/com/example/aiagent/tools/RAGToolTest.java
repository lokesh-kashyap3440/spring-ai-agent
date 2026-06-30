package com.example.aiagent.tools;

import com.example.aiagent.model.DocumentInfo;
import com.example.aiagent.service.DocumentIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RAGToolTest {

    @Mock
    private DocumentIngestionService ingestionService;

    private RAGTool tool;

    @BeforeEach
    void setUp() {
        tool = new RAGTool(ingestionService);
    }

    @Test
    void testName() {
        assertEquals("rag_search", tool.getName());
    }

    @Test
    void testDescription() {
        assertTrue(tool.getDescription().contains("Search uploaded documents"));
    }

    @Test
    void testSearchWithResults() {
        Document doc = new Document("This is the content");
        doc.getMetadata().put("filename", "doc.pdf");
        when(ingestionService.search(eq("refund policy"), anyInt()))
                .thenReturn(List.of(doc));
        when(ingestionService.listDocuments())
                .thenReturn(List.of(new DocumentInfo("1", "doc.pdf", "application/pdf", 100, 1)));

        String result = tool.execute("refund policy");
        assertTrue(result.contains("Section 1"));
        assertTrue(result.contains("doc.pdf"));
        assertTrue(result.contains("This is the content"));
    }

    @Test
    void testSearchWithNoResults() {
        when(ingestionService.search(eq("nothing"), anyInt()))
                .thenReturn(List.of());

        String result = tool.execute("nothing");
        assertTrue(result.contains("NO_RESULTS"));
    }

    @Test
    void testEmptyQuery() {
        String result = tool.execute("");
        assertEquals("Please provide a search query.", result);
        verifyNoInteractions(ingestionService);
    }

    @Test
    void testWhitespaceQuery() {
        String result = tool.execute("   ");
        assertEquals("Please provide a search query.", result);
    }

    @Test
    void testErrorHandling() {
        when(ingestionService.search(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Service unavailable"));
        String result = tool.execute("test");
        assertTrue(result.contains("Error searching documents"));
    }

    @Test
    void testNoDocumentsAvailable() {
        Document doc = new Document("content");
        doc.getMetadata().put("filename", "unknown");
        when(ingestionService.search(anyString(), anyInt())).thenReturn(List.of(doc));
        when(ingestionService.listDocuments()).thenReturn(List.of());

        String result = tool.execute("test");
        assertTrue(result.contains("No documents available."));
    }
}
