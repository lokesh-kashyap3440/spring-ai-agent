package com.example.aiagent.controller;

import com.example.aiagent.config.RagConfig;
import com.example.aiagent.model.DocumentInfo;
import com.example.aiagent.service.DocumentIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentControllerTest {

    @Mock
    private DocumentIngestionService ingestionService;

    @Mock
    private RagConfig ragConfig;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        when(ragConfig.getTopK()).thenReturn(5);
        DocumentController controller = new DocumentController(ingestionService, ragConfig);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testUploadDocument() throws Exception {
        DocumentInfo info = new DocumentInfo("doc-1", "test.pdf", "application/pdf", 1024, 3);
        when(ingestionService.ingest(any())).thenReturn(info);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "test content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.document.id").value("doc-1"))
                .andExpect(jsonPath("$.document.filename").value("test.pdf"));
    }

    @Test
    void testUploadEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    void testUploadUnsupportedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/x-msdownload", "content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported file type: application/x-msdownload"));
    }

    @Test
    void testUploadWithNullContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", null, "content".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void testListDocuments() throws Exception {
        DocumentInfo info = new DocumentInfo("doc-1", "test.pdf", "application/pdf", 100, 2);
        when(ingestionService.listDocuments()).thenReturn(List.of(info));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("doc-1"))
                .andExpect(jsonPath("$[0].filename").value("test.pdf"));
    }

    @Test
    void testListDocumentsEmpty() throws Exception {
        when(ingestionService.listDocuments()).thenReturn(List.of());

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testDeleteDocumentFound() throws Exception {
        when(ingestionService.deleteDocument("doc-1")).thenReturn(true);

        mockMvc.perform(delete("/api/documents/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"))
                .andExpect(jsonPath("$.docId").value("doc-1"));
    }

    @Test
    void testDeleteDocumentNotFound() throws Exception {
        when(ingestionService.deleteDocument("nonexistent")).thenReturn(false);

        mockMvc.perform(delete("/api/documents/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSearch() throws Exception {
        var doc = new org.springframework.ai.document.Document("content text");
        doc.getMetadata().put("filename", "doc.pdf");
        when(ingestionService.search(eq("query"), eq(5))).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents/search").param("query", "query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("query"))
                .andExpect(jsonPath("$.results").value(1))
                .andExpect(jsonPath("$.documents[0].content").value("content text"));
    }
}
