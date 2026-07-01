package com.example.aiagent.service;

import com.example.aiagent.config.OllamaConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama")
public class OllamaService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    private final WebClient webClient;
    private final OllamaConfig config;
    private final ObjectMapper objectMapper;

    public OllamaService(WebClient webClient, OllamaConfig config, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            String requestBody = buildChatRequest(systemPrompt, userMessage);

            String response = webClient.post()
                    .uri(config.getBaseUrl() + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(config.getTimeout()))
                    .onErrorResume(e -> {
                        log.error("Ollama API error: {}", e.getMessage());
                        return Mono.just("{\"message\":{\"content\":\"Error communicating with Ollama: " + e.getMessage() + "\"}}");
                    })
                    .block();

            if (response == null) {
                return "Error: No response from Ollama";
            }

            JsonNode responseJson = objectMapper.readTree(response);
            String content = responseJson.path("message").path("content").asText();
            if (content == null || content.isBlank()) {
                content = "No response";
                log.warn("Ollama returned empty content. Raw response: {}", response.length() > 500 ? response.substring(0, 500) + "..." : response);
            }
            return content;
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

        ObjectNode options = objectMapper.createObjectNode();
        options.put("num_predict", config.getMaxTokens());
        requestBody.set("options", options);

        return objectMapper.writeValueAsString(requestBody);
    }

    @Override
    public boolean isAvailable() {
        try {
            webClient.get()
                    .uri(config.getBaseUrl() + "/api/tags")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("Ollama is not available: {}", e.getMessage());
            return false;
        }
    }
}
