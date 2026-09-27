package com.example.aiagent.service;

import com.example.aiagent.config.NvidiaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)

@ExtendWith(MockitoExtension.class)
class NvidiaServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

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
        service = new NvidiaService(webClient, config, objectMapper);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.headers(any())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(any(MediaType.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void testChatSuccess() {
        String apiResponse = "{\"choices\": [{\"message\": {\"content\": \"Hello from NVIDIA!\"}}]}";
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(apiResponse));

        String result = service.chat("system prompt", "hello");

        assertEquals("Hello from NVIDIA!", result);
    }

    @Test
    void testChatApiError() {
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.error(new RuntimeException("API error")));

        String result = service.chat("system prompt", "hello");

        assertTrue(result.startsWith("Error communicating with NVIDIA API"));
    }

    @Test
    void testChatEmptyContentReturnsDefault() {
        String apiResponse = "{\"choices\": [{\"message\": {}}]}";
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(apiResponse));

        String result = service.chat("system prompt", "hello");

        assertEquals("No response", result);
    }

    @Test
    void testChatRequestIncludesAuthHeader() {
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.just("{\"choices\": [{\"message\": {\"content\": \"ok\"}}]}"));

        service.chat("system prompt", "hello");

        verify(requestBodyUriSpec).headers(any(Consumer.class));
    }

    @Test
    void testChatRequestIncludesModelAndParams() {
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.just("{\"choices\": [{\"message\": {\"content\": \"ok\"}}]}"));

        service.chat("system prompt", "hello");

        verify(requestBodyUriSpec).bodyValue(argThat(body -> {
            String str = (String) body;
            return str.contains("\"model\":\"meta/llama-3.1-8b-instruct\"")
                    && str.contains("\"temperature\":0.5")
                    && str.contains("\"max_tokens\":1024");
        }));
    }

    @Test
    void testIsAvailableReturnsTrue() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.headers(any())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just("{\"data\": []}"));

        assertTrue(service.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalse() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.headers(any())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.error(new RuntimeException("Not available")));

        assertFalse(service.isAvailable());
    }

    @Test
    void testChatWithMissingApiKey() {
        config.setApiKey("");

        String result = service.chat("system prompt", "hello");

        assertTrue(result.contains("API key is not configured"));
    }

    @Test
    void testIsAvailableWithMissingApiKey() {
        config.setApiKey("");

        assertFalse(service.isAvailable());
    }
}
