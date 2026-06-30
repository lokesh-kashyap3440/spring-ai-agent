package com.example.aiagent.service;

import com.example.aiagent.config.OllamaConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaService implements AiService {

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
            String requestBody = buildChatRequest(systemPrompt, userMessage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = config.getBaseUrl() + "/api/chat";
            String response = restTemplate.postForObject(url, entity, String.class);

            JsonNode responseJson = objectMapper.readTree(response);
            return responseJson.path("message").path("content").asText("No response");
        } catch (Exception e) {
            log.error("Ollama API error: {}", e.getMessage());
            return "Error communicating with Ollama: " + e.getMessage();
        }
    }

    private String buildChatRequest(String systemPrompt, String userMessage) throws JsonProcessingException {
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
        return objectMapper.writeValueAsString(requestBody);
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
