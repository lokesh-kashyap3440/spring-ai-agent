package com.example.aiagent.service;

import com.example.aiagent.config.NvidiaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "nvidia")
public class NvidiaService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(NvidiaService.class);

    private final RestTemplate restTemplate;
    private final NvidiaConfig config;
    private final ObjectMapper objectMapper;

    public NvidiaService(RestTemplate restTemplate, NvidiaConfig config, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            String requestBody = buildChatRequest(systemPrompt, userMessage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getApiKey());

            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String url = config.getBaseUrl() + "/chat/completions";
            String response = restTemplate.postForObject(url, entity, String.class);

            JsonNode responseJson = objectMapper.readTree(response);
            JsonNode choice = responseJson.path("choices").path(0);
            return choice.path("message").path("content").asText("No response");
        } catch (Exception e) {
            log.error("NVIDIA API error: {}", e.getMessage());
            return "Error communicating with NVIDIA API: " + e.getMessage();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(config.getApiKey());
            HttpEntity<String> entity = new HttpEntity<>(headers);
            restTemplate.getForObject(config.getBaseUrl() + "/models", String.class, entity);
            return true;
        } catch (Exception e) {
            log.warn("NVIDIA API is not available: {}", e.getMessage());
            return false;
        }
    }

    private String buildChatRequest(String systemPrompt, String userMessage) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", config.getModel());
        requestBody.put("temperature", config.getTemperature());
        requestBody.put("max_tokens", config.getMaxTokens());
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
}
