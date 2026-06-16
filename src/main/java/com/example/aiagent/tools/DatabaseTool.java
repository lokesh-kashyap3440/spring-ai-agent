package com.example.aiagent.tools;

import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Component
public class DatabaseTool implements Tool {

    private final StringRedisTemplate redisTemplate;
    private final Map<String, String> knowledgeBase;

    public DatabaseTool(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.knowledgeBase = new HashMap<>();
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

            String cacheKey = "db:" + query;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return "From cache: " + cached;
            }

            for (Map.Entry<String, String> entry : knowledgeBase.entrySet()) {
                if (query.contains(entry.getKey())) {
                    redisTemplate.opsForValue().set(cacheKey, entry.getValue(), 5, TimeUnit.MINUTES);
                    return "Found: " + entry.getValue();
                }
            }

            String result = "No specific information found for: " + query + ". Available topics: " + String.join(", ", knowledgeBase.keySet());
            return result;
        } catch (Exception e) {
            return "Database error: " + e.getMessage();
        }
    }
}
