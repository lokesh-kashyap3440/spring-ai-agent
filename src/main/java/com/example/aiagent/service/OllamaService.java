package com.example.aiagent.service;

import com.example.aiagent.config.OllamaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final RestTemplate restTemplate;
    private final OllamaConfig config;
    private final ObjectMapper objectMapper;

    public OllamaService(RestTemplate restTemplate, OllamaConfig config, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public String chat(String systemPrompt, String userMessage) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", config.getModel());
            requestBody.put("stream", false);

            ArrayNode messages = objectMapper.createArrayNode();

            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                ObjectNode systemMsg = objectMapper.createObjectNode();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                messages.add(systemMsg);
            }

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            requestBody.set("messages", messages);

            String url = config.getBaseUrl() + "/api/chat";
            String response = restTemplate.postForObject(url, requestBody, String.class);

            JsonNode responseJson = objectMapper.readTree(response);
            return responseJson.path("message").path("content").asText("No response");
        } catch (Exception e) {
            log.error("Ollama API error: {}", e.getMessage());
            return "Error communicating with Ollama: " + e.getMessage();
        }
    }

    public boolean isAvailable() {
        try {
            restTemplate.getForObject(config.getBaseUrl() + "/api/tags", String.class);
            return true;
        } catch (Exception e) {
            log.warn("Ollama is not available: {}", e.getMessage());
            return false;
        }
    }
}
