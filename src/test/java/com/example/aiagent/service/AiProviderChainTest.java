package com.example.aiagent.service;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiProviderChainTest {

    @Mock
    private NvidiaService nvidiaService;

    @Mock
    private OllamaService ollamaService;

    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
    }

    @Test
    void testChatDelegatesToNvidiaWhenConfigured() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), circuitBreakerRegistry, "nvidia");
        when(nvidiaService.chat(anyString(), anyString())).thenReturn("nvidia response");

        String result = chain.chat("system", "hello");

        assertEquals("nvidia response", result);
        verify(nvidiaService).chat("system", "hello");
        verify(ollamaService, never()).chat(anyString(), anyString());
    }

    @Test
    void testChatFallsBackToOllamaWhenNvidiaFails() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), circuitBreakerRegistry, "nvidia");
        when(nvidiaService.chat(anyString(), anyString())).thenThrow(new RuntimeException("API error"));
        when(ollamaService.chat(anyString(), anyString())).thenReturn("ollama response");

        String result = chain.chat("system", "hello");

        assertEquals("ollama response", result);
        verify(nvidiaService).chat("system", "hello");
        verify(ollamaService).chat("system", "hello");
    }

    @Test
    void testChatDelegatesToOllamaWhenConfigured() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), circuitBreakerRegistry, "ollama");
        when(ollamaService.chat(anyString(), anyString())).thenReturn("ollama response");

        String result = chain.chat("system", "hello");

        assertEquals("ollama response", result);
        verify(ollamaService).chat("system", "hello");
        verify(nvidiaService, never()).chat(anyString(), anyString());
    }

    @Test
    void testChatReturnsErrorWhenProviderThrows() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService), circuitBreakerRegistry, "nvidia");
        when(nvidiaService.chat(anyString(), anyString())).thenThrow(new RuntimeException("Connection refused"));

        String result = chain.chat("system", "hello");

        assertTrue(result.contains("unavailable"), "Expected error message about unavailability");
        verify(nvidiaService).chat("system", "hello");
    }

    @Test
    void testIsAvailableReturnsTrueWhenNvidiaAvailable() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), circuitBreakerRegistry, "nvidia");
        when(nvidiaService.isAvailable()).thenReturn(true);

        assertTrue(chain.isAvailable());
    }

    @Test
    void testIsAvailableReturnsTrueWhenOllamaConfigured() {
        when(ollamaService.isAvailable()).thenReturn(true);
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), circuitBreakerRegistry, "ollama");

        assertTrue(chain.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalseWhenNeitherAvailable() {
        when(nvidiaService.isAvailable()).thenReturn(false);
        when(ollamaService.isAvailable()).thenReturn(false);

        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), circuitBreakerRegistry, "nvidia");

        assertFalse(chain.isAvailable());
    }

    @Test
    void testChatFallsBackWhenConfiguredProviderUnavailableAndListedFirst() {
        AiProviderChain chain = new AiProviderChain(
                List.of(ollamaService, nvidiaService), circuitBreakerRegistry, "nvidia");
        when(nvidiaService.chat(anyString(), anyString())).thenThrow(new RuntimeException("NVIDIA down"));
        when(ollamaService.chat(anyString(), anyString())).thenReturn("ollama response");

        String result = chain.chat("system", "hello");

        assertEquals("ollama response", result);
        verify(ollamaService).chat("system", "hello");
        verify(nvidiaService).chat("system", "hello");
    }

    @Test
    void testConfiguredProviderPreferredEvenWhenNotFirstInList() {
        AiProviderChain chain = new AiProviderChain(
                List.of(ollamaService, nvidiaService), circuitBreakerRegistry, "nvidia");
        when(nvidiaService.isAvailable()).thenReturn(true);
        when(nvidiaService.chat(anyString(), anyString())).thenReturn("nvidia response");

        String result = chain.chat("system", "hello");

        assertEquals("nvidia response", result);
        verify(nvidiaService).chat("system", "hello");
        verify(ollamaService, never()).chat(anyString(), anyString());
    }

    @Test
    void testTokenAccumulationFromSuccessfulProvider() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), circuitBreakerRegistry, "nvidia");
        when(nvidiaService.chat(anyString(), anyString())).thenReturn("nvidia response");
        when(nvidiaService.getLastPromptTokens()).thenReturn(100);
        when(nvidiaService.getLastCompletionTokens()).thenReturn(50);

        chain.chat("system", "hello");

        assertEquals(100, chain.getTotalPromptTokens());
        assertEquals(50, chain.getTotalCompletionTokens());
        assertEquals(150, chain.getTotalTokens());
    }
}
