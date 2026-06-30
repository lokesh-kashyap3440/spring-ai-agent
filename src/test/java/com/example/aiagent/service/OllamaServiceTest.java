package com.example.aiagent.service;

import com.example.aiagent.config.OllamaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private OllamaConfig config;
    private ObjectMapper objectMapper;
    private OllamaService service;

    @BeforeEach
    void setUp() {
        config = new OllamaConfig();
        config.setBaseUrl("http://localhost:11434");
        config.setModel("qwen3.5:4b");
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new OllamaService(restTemplate, config, objectMapper);
    }

    @Test
    void testChatSuccess() {
        String apiResponse = "{\"message\": {\"content\": \"Hello! How can I help you?\"}}";
        when(restTemplate.postForObject(eq("http://localhost:11434/api/chat"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(apiResponse);

        String result = service.chat("system prompt", "hello");

        assertEquals("Hello! How can I help you?", result);
    }

    @Test
    void testChatApiError() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = service.chat("system prompt", "hello");

        assertTrue(result.startsWith("Error communicating with Ollama"));
    }

    @Test
    void testChatEmptyContentReturnsDefault() {
        String apiResponse = "{\"message\": {}}";
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(apiResponse);

        String result = service.chat("system prompt", "hello");

        assertEquals("No response", result);
    }

    @Test
    void testIsAvailableReturnsTrue() {
        when(restTemplate.getForObject("http://localhost:11434/api/tags", String.class))
                .thenReturn("{\"models\": []}");

        assertTrue(service.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalse() {
        when(restTemplate.getForObject("http://localhost:11434/api/tags", String.class))
                .thenThrow(new RuntimeException("Not available"));

        assertFalse(service.isAvailable());
    }

    @Test
    void testChatRequestIncludesSystemPrompt() throws Exception {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"message\": {\"content\": \"ok\"}}");

        service.chat("You are a helpful assistant", "tell me a joke");

        verify(restTemplate).postForObject(eq("http://localhost:11434/api/chat"),
                argThat((HttpEntity<String> entity) -> {
                    String body = entity.getBody();
                    return body.contains("\"role\":\"system\"")
                            && body.contains("\"role\":\"user\"")
                            && body.contains("\"model\":\"qwen3.5:4b\"")
                            && body.contains("\"stream\":false");
                }),
                eq(String.class));
    }
}
