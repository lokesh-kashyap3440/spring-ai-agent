package com.example.aiagent.service;

import com.example.aiagent.config.OllamaConfig;
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

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)

@ExtendWith(MockitoExtension.class)
class OllamaServiceTest {

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
        service = new OllamaService(webClient, config, objectMapper);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.bodyValue(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void testChatSuccess() {
        String apiResponse = "{\"message\": {\"content\": \"Hello! How can I help you?\"}}";
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(apiResponse));

        String result = service.chat("system prompt", "hello");

        assertEquals("Hello! How can I help you?", result);
    }

    @Test
    void testChatApiError() {
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        String result = service.chat("system prompt", "hello");

        assertTrue(result.startsWith("Error communicating with Ollama"));
    }

    @Test
    void testChatEmptyContentReturnsDefault() {
        String apiResponse = "{\"message\": {}}";
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just(apiResponse));

        String result = service.chat("system prompt", "hello");

        assertEquals("No response", result);
    }

    @Test
    void testIsAvailableReturnsTrue() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(Mono.just("{\"models\": []}"));

        assertTrue(service.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalse() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.error(new RuntimeException("Not available")));

        assertFalse(service.isAvailable());
    }

    @Test
    void testChatRequestIncludesSystemPrompt() {
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.just("{\"message\": {\"content\": \"ok\"}}"));

        service.chat("You are a helpful assistant", "tell me a joke");

        verify(requestBodyUriSpec).bodyValue(argThat(body -> {
            String str = (String) body;
            return str.contains("\"role\":\"system\"")
                    && str.contains("\"role\":\"user\"")
                    && str.contains("\"model\":\"qwen3.5:4b\"")
                    && str.contains("\"stream\":false");
        }));
    }
}
