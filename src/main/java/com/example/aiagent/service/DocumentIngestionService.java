package com.example.aiagent.service;

import com.example.aiagent.config.RagConfig;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles document ingestion, storage, and retrieval for the RAG pipeline.
 *
 * <p>The ingestion pipeline: Tika parsing &rarr; token text splitting &rarr;
 * embedding via configured model &rarr; storage in PgVectorStore &rarr; metadata
 * persisted to PostgreSQL.</p>
 *
 * <p>Search uses cosine similarity against the vector store with a configurable
 * top-K and similarity threshold from {@link RagConfig}.</p>
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final JdbcTemplate jdbc;
    private final RagConfig ragConfig;

    public DocumentIngestionService(VectorStore vectorStore, JdbcTemplate jdbc, RagConfig ragConfig) {
        this.vectorStore = vectorStore;
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        this.jdbc = jdbc;
        this.ragConfig = ragConfig;
    }

    @Transactional
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
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .similarityThreshold(ragConfig.getSimilarityThreshold())
                            .build()
            );
        } catch (Exception e) {
            log.error("Vector search failed for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    public long countEntries(String deityName) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(deityName + " temple")
                        .topK(500)
                        .similarityThreshold(0.0)
                        .build()
        );

        long count = 0;
        for (Document doc : results) {
            String text = doc.getText();
            for (String line : text.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length >= 4) {
                    String templeName = (parts[0] + " " + parts[1]).toLowerCase();
                    String deity = parts[2].toLowerCase();
                    String search = deityName.toLowerCase();
                    if (templeName.equals(search + " temple") || deity.equals(search)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public List<Document> search(String query) {
        return search(query, ragConfig.getTopK());
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
            log.info("Document {} removed from metadata index", docId);
            log.warn("Vector store chunks for docId={} are not cleaned up automatically. "
                    + "A future improvement should track vector document IDs for proper deletion.", docId);
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
