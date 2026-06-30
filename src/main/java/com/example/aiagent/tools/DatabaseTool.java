package com.example.aiagent.tools;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DatabaseTool implements Tool {

    private final Map<String, String> knowledgeBase;

    public DatabaseTool() {
        this.knowledgeBase = new ConcurrentHashMap<>();
        initializeKnowledgeBase();
    }

    private void initializeKnowledgeBase() {
        knowledgeBase.put("spring boot", "Spring Boot is a framework for building production-ready Java applications. It provides auto-configuration and embedded servers.");
        knowledgeBase.put("kafka", "Apache Kafka is a distributed event streaming platform for building real-time data pipelines and streaming apps.");
        knowledgeBase.put("redis", "Redis is an in-memory data store used as a database, cache, and message broker.");
        knowledgeBase.put("ollama", "Ollama is a tool for running large language models locally on your machine.");
        knowledgeBase.put("ai", "Artificial Intelligence (AI) refers to systems that can perform tasks requiring human-like intelligence.");
    }

    @Override
    public String getName() {
        return "database";
    }

    @Override
    public String getDescription() {
        return "Query the knowledge base for information. Input: search query (e.g., 'What is Spring Boot?')";
    }

    @Override
    public String execute(String input) {
        try {
            String query = input.trim().toLowerCase();

            for (Map.Entry<String, String> entry : knowledgeBase.entrySet()) {
                if (query.contains(entry.getKey())) {
                    return "Found: " + entry.getValue();
                }
            }

            return "No specific information found for: " + query + ". Available topics: " + String.join(", ", knowledgeBase.keySet());
        } catch (Exception e) {
            return "Database error: " + e.getMessage();
        }
    }
}
