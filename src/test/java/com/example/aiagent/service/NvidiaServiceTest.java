package com.example.aiagent.service;

import com.example.aiagent.config.NvidiaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NvidiaServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private NvidiaConfig config;
    private ObjectMapper objectMapper;
    private NvidiaService service;

    @BeforeEach
    void setUp() {
        config = new NvidiaConfig();
        config.setApiKey("test-api-key");
        config.setModel("meta/llama-3.1-8b-instruct");
        config.setBaseUrl("https://integrate.api.nvidia.com/v1");
        config.setTemperature(0.5);
        config.setMaxTokens(1024);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new NvidiaService(restTemplate, config, objectMapper);
    }

    @Test
    void testChatSuccess() {
        String apiResponse = "{\"choices\": [{\"message\": {\"content\": \"Hello from NVIDIA!\"}}]}";
        when(restTemplate.postForObject(eq("https://integrate.api.nvidia.com/v1/chat/completions"),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(apiResponse);

        String result = service.chat("system prompt", "hello");

        assertEquals("Hello from NVIDIA!", result);
    }

    @Test
    void testChatApiError() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("API error"));

        String result = service.chat("system prompt", "hello");

        assertTrue(result.startsWith("Error communicating with NVIDIA API"));
    }

    @Test
    void testChatEmptyContentReturnsDefault() {
        String apiResponse = "{\"choices\": [{\"message\": {}}]}";
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(apiResponse);

        String result = service.chat("system prompt", "hello");

        assertEquals("No response", result);
    }

    @Test
    void testChatRequestIncludesAuthHeader() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"choices\": [{\"message\": {\"content\": \"ok\"}}]}");

        service.chat("system prompt", "hello");

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(String.class));

        HttpEntity<String> entity = captor.getValue();
        assertNotNull(entity.getHeaders().get("Authorization"));
        assertTrue(entity.getHeaders().get("Authorization").get(0).contains("Bearer test-api-key"));
    }

    @Test
    void testChatRequestIncludesModelAndParams() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"choices\": [{\"message\": {\"content\": \"ok\"}}]}");

        service.chat("system prompt", "hello");

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(String.class));

        String body = captor.getValue().getBody();
        assertTrue(body.contains("\"model\":\"meta/llama-3.1-8b-instruct\""));
        assertTrue(body.contains("\"temperature\":0.5"));
        assertTrue(body.contains("\"max_tokens\":1024"));
        assertTrue(body.contains("\"stream\":false"));
    }

    @Test
    void testIsAvailableReturnsTrue() {
        when(restTemplate.getForObject(anyString(), eq(String.class), any(HttpEntity.class)))
                .thenReturn("{\"data\": []}");

        assertTrue(service.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalse() {
        when(restTemplate.getForObject(anyString(), eq(String.class), any(HttpEntity.class)))
                .thenThrow(new RuntimeException("Not available"));

        assertFalse(service.isAvailable());
    }
}
