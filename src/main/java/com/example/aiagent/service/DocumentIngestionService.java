package com.example.aiagent.service;

import com.example.aiagent.model.DocumentInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final String DOC_INDEX_KEY = "rag:documents";
    private static final int DEFAULT_TOP_K = 3;

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public DocumentIngestionService(VectorStore vectorStore,
                                    StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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

        chunks.forEach(chunk -> {
            chunk.getMetadata().put("docId", docId);
            chunk.getMetadata().put("filename", filename);
            chunk.getMetadata().put("contentType", file.getContentType());
            chunk.getMetadata().put("uploadTime", Instant.now().toString());
        });

        vectorStore.add(chunks);

        DocumentInfo info = new DocumentInfo(docId, filename, file.getContentType(),
                file.getSize(), chunks.size());
        saveDocumentInfo(info);

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
        try {
            String json = redisTemplate.opsForValue().get(DOC_INDEX_KEY);
            if (json == null || json.isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<DocumentInfo>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize document index", e);
            return new ArrayList<>();
        }
    }

    public boolean deleteDocument(String docId) {
        List<DocumentInfo> docs = listDocuments();
        boolean removed = docs.removeIf(doc -> doc.getId().equals(docId));

        if (removed) {
            saveDocumentList(docs);
            log.info("Document {} removed from index", docId);
        }

        return removed;
    }

    private void saveDocumentInfo(DocumentInfo info) {
        List<DocumentInfo> docs = listDocuments();
        docs.add(info);
        saveDocumentList(docs);
    }

    private void saveDocumentList(List<DocumentInfo> docs) {
        try {
            String json = objectMapper.writeValueAsString(docs);
            redisTemplate.opsForValue().set(DOC_INDEX_KEY, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize document index", e);
        }
    }
}
