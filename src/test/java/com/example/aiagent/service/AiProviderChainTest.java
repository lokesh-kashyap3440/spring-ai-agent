package com.example.aiagent.service;

import com.example.aiagent.config.OllamaConfig;
import com.example.aiagent.config.NvidiaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiProviderChainTest {

    @Mock
    private NvidiaService nvidiaService;

    @Mock
    private OllamaService ollamaService;

    @Mock
    private ObjectProvider<NvidiaService> nvidiaProvider;

    @Mock
    private ObjectProvider<OllamaService> ollamaProvider;

    @BeforeEach
    void setUp() {
        lenient().when(nvidiaProvider.getIfAvailable()).thenReturn(nvidiaService);
        lenient().when(ollamaProvider.getIfAvailable()).thenReturn(ollamaService);
    }

    @Test
    void testChatDelegatesToNvidiaWhenConfigured() {
        AiProviderChain chain = new AiProviderChain(nvidiaProvider, ollamaProvider, "nvidia");
        when(nvidiaService.isAvailable()).thenReturn(true);
        when(nvidiaService.chat(anyString(), anyString())).thenReturn("nvidia response");

        String result = chain.chat("system", "hello");

        assertEquals("nvidia response", result);
        verify(nvidiaService).chat("system", "hello");
        verify(ollamaService, never()).chat(anyString(), anyString());
    }

    @Test
    void testChatFallsBackToOllamaWhenNvidiaUnavailable() {
        AiProviderChain chain = new AiProviderChain(nvidiaProvider, ollamaProvider, "nvidia");
        when(nvidiaService.isAvailable()).thenReturn(false);
        when(ollamaService.chat(anyString(), anyString())).thenReturn("ollama response");

        String result = chain.chat("system", "hello");

        assertEquals("ollama response", result);
        verify(ollamaService).chat("system", "hello");
    }

    @Test
    void testChatDelegatesToOllamaWhenConfigured() {
        AiProviderChain chain = new AiProviderChain(nvidiaProvider, ollamaProvider, "ollama");
        when(ollamaService.chat(anyString(), anyString())).thenReturn("ollama response");

        String result = chain.chat("system", "hello");

        assertEquals("ollama response", result);
        verify(ollamaService).chat("system", "hello");
        verify(nvidiaService, never()).chat(anyString(), anyString());
    }

    @Test
    void testChatReturnsErrorWhenNoProviderAvailable() {
        when(nvidiaProvider.getIfAvailable()).thenReturn(null);
        when(ollamaProvider.getIfAvailable()).thenReturn(null);

        AiProviderChain chain = new AiProviderChain(nvidiaProvider, ollamaProvider, "nvidia");

        String result = chain.chat("system", "hello");
        assertEquals("Error: No AI provider is available.", result);
    }

    @Test
    void testIsAvailableReturnsTrueWhenNvidiaAvailable() {
        AiProviderChain chain = new AiProviderChain(nvidiaProvider, ollamaProvider, "nvidia");
        when(nvidiaService.isAvailable()).thenReturn(true);

        assertTrue(chain.isAvailable());
    }

    @Test
    void testIsAvailableReturnsTrueWhenOllamaConfigured() {
        when(ollamaService.isAvailable()).thenReturn(true);
        AiProviderChain chain = new AiProviderChain(nvidiaProvider, ollamaProvider, "ollama");

        assertTrue(chain.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalseWhenNeitherAvailable() {
        when(nvidiaProvider.getIfAvailable()).thenReturn(null);
        when(ollamaProvider.getIfAvailable()).thenReturn(null);

        AiProviderChain chain = new AiProviderChain(nvidiaProvider, ollamaProvider, "nvidia");

        assertFalse(chain.isAvailable());
    }
}
