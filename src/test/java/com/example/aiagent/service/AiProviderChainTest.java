package com.example.aiagent.service;

import com.example.aiagent.config.OllamaConfig;
import com.example.aiagent.config.NvidiaConfig;
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

    @BeforeEach
    void setUp() {
        lenient().when(nvidiaService.isAvailable()).thenReturn(true);
        lenient().when(ollamaService.isAvailable()).thenReturn(true);
    }

    @Test
    void testChatDelegatesToNvidiaWhenConfigured() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), "nvidia");
        when(nvidiaService.chat(anyString(), anyString())).thenReturn("nvidia response");

        String result = chain.chat("system", "hello");

        assertEquals("nvidia response", result);
        verify(nvidiaService).chat("system", "hello");
        verify(ollamaService, never()).chat(anyString(), anyString());
    }

    @Test
    void testChatFallsBackToOllamaWhenNvidiaUnavailable() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), "nvidia");
        when(nvidiaService.isAvailable()).thenReturn(false);
        when(ollamaService.chat(anyString(), anyString())).thenReturn("ollama response");

        String result = chain.chat("system", "hello");

        assertEquals("ollama response", result);
        verify(ollamaService).chat("system", "hello");
    }

    @Test
    void testChatDelegatesToOllamaWhenConfigured() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), "ollama");
        when(ollamaService.chat(anyString(), anyString())).thenReturn("ollama response");

        String result = chain.chat("system", "hello");

        assertEquals("ollama response", result);
        verify(ollamaService).chat("system", "hello");
        verify(nvidiaService, never()).chat(anyString(), anyString());
    }

    @Test
    void testChatReturnsErrorWhenNoProviderAvailable() {
        AiProviderChain chain = new AiProviderChain(
                List.of(), "nvidia");

        String result = chain.chat("system", "hello");
        assertEquals("Error: No AI provider is available.", result);
    }

    @Test
    void testIsAvailableReturnsTrueWhenNvidiaAvailable() {
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), "nvidia");
        when(nvidiaService.isAvailable()).thenReturn(true);

        assertTrue(chain.isAvailable());
    }

    @Test
    void testIsAvailableReturnsTrueWhenOllamaConfigured() {
        when(ollamaService.isAvailable()).thenReturn(true);
        AiProviderChain chain = new AiProviderChain(
                List.of(nvidiaService, ollamaService), "ollama");

        assertTrue(chain.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalseWhenNeitherAvailable() {
        AiProviderChain chain = new AiProviderChain(
                List.of(), "nvidia");

        assertFalse(chain.isAvailable());
    }

    @Test
    void testChatFallsBackWhenConfiguredProviderUnavailableAndListedFirst() {
        AiProviderChain chain = new AiProviderChain(
                List.of(ollamaService, nvidiaService), "nvidia");
        when(ollamaService.chat(anyString(), anyString())).thenReturn("ollama response");

        // nvidia is configured but unavailable, should fall back to ollama
        when(nvidiaService.isAvailable()).thenReturn(false);

        String result = chain.chat("system", "hello");
        assertEquals("ollama response", result);
        verify(ollamaService).chat("system", "hello");
    }

    @Test
    void testConfiguredProviderPreferredEvenWhenNotFirstInList() {
        AiProviderChain chain = new AiProviderChain(
                List.of(ollamaService, nvidiaService), "nvidia");
        when(nvidiaService.isAvailable()).thenReturn(true);
        when(nvidiaService.chat(anyString(), anyString())).thenReturn("nvidia response");

        String result = chain.chat("system", "hello");
        assertEquals("nvidia response", result);
        verify(nvidiaService).chat("system", "hello");
        verify(ollamaService, never()).chat(anyString(), anyString());
    }
}
