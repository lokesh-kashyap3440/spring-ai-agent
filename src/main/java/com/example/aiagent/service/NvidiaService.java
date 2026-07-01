package com.example.aiagent.service;

import com.example.aiagent.config.NvidiaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "nvidia", matchIfMissing = true)
public class NvidiaService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(NvidiaService.class);

    private final WebClient webClient;
    private final NvidiaConfig config;
    private final ObjectMapper objectMapper;

    public NvidiaService(WebClient webClient, NvidiaConfig config, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("NVIDIA API key is not configured");
            return "Error: NVIDIA API key is not configured. Set NVIDIA_API_KEY environment variable.";
        }
        try {
            String requestBody = buildChatRequest(systemPrompt, userMessage);

            String response = webClient.post()
                    .uri(config.getBaseUrl() + "/chat/completions")
                    .headers(h -> h.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(config.getTimeout()))
                    .onErrorResume(e -> {
                        log.error("NVIDIA API error: {}", e.getMessage());
                        return Mono.just("{\"choices\":[{\"message\":{\"content\":\"Error communicating with NVIDIA API: " + e.getMessage() + "\"}}]}");
                    })
                    .block();

            if (response == null) {
                return "No response";
            }

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
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("NVIDIA API key is not configured");
            return false;
        }
        try {
            webClient.get()
                    .uri(config.getBaseUrl() + "/models")
                    .headers(h -> h.setBearerAuth(apiKey))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
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
