package com.example.aiagent.service;

import com.example.aiagent.model.DocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final int DEFAULT_TOP_K = 3;

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final JdbcTemplate jdbc;

    public DocumentIngestionService(VectorStore vectorStore, JdbcTemplate jdbc) {
        this.vectorStore = vectorStore;
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        this.jdbc = jdbc;
    }

    public DocumentInfo ingest(MultipartFile file) throws IOException {
        String docId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();

        log.info("Ingesting document: {} (id={})", filename, docId);

        TikaDocumentReader tikaReader = new TikaDocumentReader(file.getResource());
        List<Document> documents = tikaReader.get();

        if (documents.isEmpty()) {
            throw new IOException("Could not read content from file: " + filename);
        }

        List<Document> chunks = textSplitter.apply(documents);
        log.info("Split document '{}' into {} chunks", filename, chunks.size());

        Timestamp now = Timestamp.from(Instant.now());
        chunks.forEach(chunk -> {
            chunk.getMetadata().put("docId", docId);
            chunk.getMetadata().put("filename", filename);
            chunk.getMetadata().put("contentType", file.getContentType());
            chunk.getMetadata().put("uploadTime", now.toString());
        });

        vectorStore.add(chunks);

        jdbc.update("INSERT INTO document_metadata (doc_id, filename, content_type, size, chunks, uploaded_at) VALUES (?, ?, ?, ?, ?, ?)",
                docId, filename, file.getContentType(), file.getSize(), chunks.size(), now);

        DocumentInfo info = new DocumentInfo(docId, filename, file.getContentType(),
                file.getSize(), chunks.size());
        log.info("Document '{}' ingested successfully: {} chunks stored", filename, chunks.size());
        return info;
    }

    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).similarityThreshold(0.5).build()
        );
    }

    public List<Document> search(String query) {
        return search(query, DEFAULT_TOP_K);
    }

    public List<DocumentInfo> listDocuments() {
        return jdbc.query(
                "SELECT doc_id, filename, content_type, size, chunks, uploaded_at FROM document_metadata ORDER BY uploaded_at DESC",
                this::mapDocumentInfo
        );
    }

    public boolean deleteDocument(String docId) {
        int updated = jdbc.update("DELETE FROM document_metadata WHERE doc_id = ?", docId);
        if (updated > 0) {
            log.info("Document {} removed from index", docId);
        }
        return updated > 0;
    }

    private DocumentInfo mapDocumentInfo(ResultSet rs, int row) throws java.sql.SQLException {
        DocumentInfo info = new DocumentInfo();
        info.setId(rs.getString("doc_id"));
        info.setFilename(rs.getString("filename"));
        info.setContentType(rs.getString("content_type"));
        info.setSize(rs.getLong("size"));
        info.setChunks(rs.getInt("chunks"));
        Timestamp ts = rs.getTimestamp("uploaded_at");
        if (ts != null) {
            info.setUploadedAt(ts.toInstant());
        }
        return info;
    }
}
