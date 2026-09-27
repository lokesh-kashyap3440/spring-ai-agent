package com.example.aiagent.service;

import com.example.aiagent.config.LmStudioConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * AI provider implementation for LM Studio.
 * <p>
 * Uses LM Studio's OpenAI-compatible API endpoint ({@code /v1/chat/completions}).
 * Modeled after safe-react-agent's multi-provider fallback pattern:
 * Primary: LM Studio (local), Fallback: NVIDIA NIM / OpenAI.
 */
@Service
public class LmStudioService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(LmStudioService.class);

    private final WebClient webClient;
    private final LmStudioConfig config;
    private final ObjectMapper objectMapper;

    public LmStudioService(WebClient webClient, LmStudioConfig config, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            String requestBody = buildChatRequest(systemPrompt, userMessage);

            String response = webClient.post()
                    .uri(config.getBaseUrl() + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(config.getTimeout()))
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .maxBackoff(Duration.ofSeconds(10))
                            .doBeforeRetry(rs -> log.warn("LM Studio API call failed, retrying... attempt {}",
                                    rs.totalRetries() + 1)))
                    .onErrorResume(e -> {
                        log.error("LM Studio API error after retries: {}", e.getMessage());
                        return Mono.just("{\"choices\":[{\"message\":{\"content\":\"Error communicating with LM Studio: " + e.getMessage() + "\"}}]}");
                    })
                    .block();

            if (response == null) {
                return "Error: No response from LM Studio";
            }

            JsonNode responseJson = objectMapper.readTree(response);
            JsonNode choice = responseJson.path("choices").path(0);
            String content = choice.path("message").path("content").asText("No response");
            if (content == null || content.isBlank()) {
                content = "No response";
                log.warn("LM Studio returned empty content. Raw response: {}", response.length() > 500 ? response.substring(0, 500) + "..." : response);
            }
            return content;
        } catch (Exception e) {
            log.error("LM Studio API error: {}", e.getMessage());
            return "Error communicating with LM Studio: " + e.getMessage();
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

    @Override
    public boolean isAvailable() {
        try {
            String modelsResponse = webClient.get()
                    .uri(config.getBaseUrl() + "/models")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (modelsResponse == null || modelsResponse.isBlank()) {
                return false;
            }

            JsonNode root = objectMapper.readTree(modelsResponse);
            JsonNode data = root.path("data");
            return data.isArray() && data.size() > 0;
        } catch (Exception e) {
            log.warn("LM Studio is not available: {}", e.getMessage());
            return false;
        }
    }
}