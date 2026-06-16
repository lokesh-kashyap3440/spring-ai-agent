package com.example.aiagent.controller;

import com.example.aiagent.model.DocumentInfo;
import com.example.aiagent.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown",
            "text/html"
    );

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "File is empty"
                ));
            }

            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Unsupported file type: " + contentType,
                        "allowed", ALLOWED_TYPES
                ));
            }

            log.info("Uploading document: {} (type={}, size={})",
                    file.getOriginalFilename(), contentType, file.getSize());

            DocumentInfo info = ingestionService.ingest(file);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "document", info
            ));

        } catch (IOException e) {
            log.error("Failed to upload document", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to process document: " + e.getMessage()
            ));
        }
    }

    @GetMapping
    public ResponseEntity<List<DocumentInfo>> listDocuments() {
        return ResponseEntity.ok(ingestionService.listDocuments());
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Map<String, String>> deleteDocument(@PathVariable String docId) {
        boolean removed = ingestionService.deleteDocument(docId);
        if (removed) {
            return ResponseEntity.ok(Map.of(
                    "status", "deleted",
                    "docId", docId
            ));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query) {
        var results = ingestionService.search(query, 3);
        return ResponseEntity.ok(Map.of(
                "query", query,
                "results", results.size(),
                "documents", results.stream()
                        .map(doc -> Map.of(
                                "content", doc.getText(),
                                "metadata", doc.getMetadata()
                        ))
                        .toList()
        ));
    }
}
