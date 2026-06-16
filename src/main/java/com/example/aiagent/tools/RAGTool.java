package com.example.aiagent.tools;

import com.example.aiagent.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RAGTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(RAGTool.class);

    private final DocumentIngestionService ingestionService;

    public RAGTool(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public String getName() {
        return "rag_search";
    }

    @Override
    public String getDescription() {
        return "Search uploaded documents for relevant information. Input: search query (e.g., 'What is the refund policy?')";
    }

    @Override
    public String execute(String input) {
        try {
            String query = input.trim();
            if (query.isEmpty()) {
                return "Please provide a search query.";
            }

            log.info("RAG search query: {}", query);

            List<Document> results = ingestionService.search(query, 3);

            if (results.isEmpty()) {
                return "No relevant information found in uploaded documents for: " + query;
            }

            StringBuilder response = new StringBuilder();
            response.append("Found ").append(results.size()).append(" relevant sections:\n\n");

            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown");
                String content = doc.getText();

                response.append("--- Section ").append(i + 1)
                        .append(" (from: ").append(filename).append(") ---\n");
                response.append(content).append("\n\n");
            }

            return response.toString();

        } catch (Exception e) {
            log.error("RAG search failed", e);
            return "Error searching documents: " + e.getMessage();
        }
    }
}
