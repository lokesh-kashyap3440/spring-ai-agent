package com.example.aiagent.controller;

import com.example.aiagent.config.RagConfig;
import com.example.aiagent.model.DocumentInfo;
import com.example.aiagent.security.JwtUtil;
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
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown",
            "text/html"
    );

    private final DocumentIngestionService ingestionService;
    private final RagConfig ragConfig;
    private final JwtUtil jwtUtil;

    public DocumentController(DocumentIngestionService ingestionService, RagConfig ragConfig, JwtUtil jwtUtil) {
        this.ingestionService = ingestionService;
        this.ragConfig = ragConfig;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String authHeader) {
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

            if (file.getSize() > MAX_FILE_SIZE) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "File size exceeds maximum allowed size of 50MB"
                ));
            }

            String username = extractUsername(authHeader);
            if (username == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid or missing authorization token"));
            }

            log.info("Uploading document: {} (type={}, size={}) by user: {}",
                    file.getOriginalFilename(), contentType, file.getSize(), username);

            DocumentInfo info = ingestionService.ingest(file, username);

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
    public ResponseEntity<List<DocumentInfo>> listDocuments(@RequestHeader("Authorization") String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(401).body(List.of());
        }
        return ResponseEntity.ok(ingestionService.listDocuments(username));
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable String docId,
            @RequestHeader("Authorization") String authHeader) {
        String username = extractUsername(authHeader);
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or missing authorization token"));
        }
        boolean removed = ingestionService.deleteDocument(docId, username);
        if (removed) {
            return ResponseEntity.ok(Map.of(
                    "status", "deleted",
                    "docId", docId
            ));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Extracts the username from the JWT token in the Authorization header.
     *
     * @param authHeader the Authorization header value (Bearer token)
     * @return the username, or null if the header is invalid
     */
    private String extractUsername(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        try {
            return jwtUtil.extractUsername(token);
        } catch (Exception e) {
            log.warn("Failed to extract username from token: {}", e.getMessage());
            return null;
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query) {
        var results = ingestionService.search(query, ragConfig.getTopK());
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
