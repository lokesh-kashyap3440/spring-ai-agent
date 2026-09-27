package com.example.aiagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OllamaConfigTest {

    @Test
    void testDefaultValues() {
        OllamaConfig config = new OllamaConfig();
        assertEquals("http://localhost:11434", config.getBaseUrl());
        assertEquals("llama3.2:3b", config.getModel());
        assertEquals(120, config.getTimeout());
        assertEquals(2048, config.getMaxTokens());
    }

    @Test
    void testSetters() {
        OllamaConfig config = new OllamaConfig();
        config.setBaseUrl("http://other:11434");
        config.setModel("llama3");
        config.setTimeout(60);
        config.setMaxTokens(4096);

        assertEquals("http://other:11434", config.getBaseUrl());
        assertEquals("llama3", config.getModel());
        assertEquals(60, config.getTimeout());
        assertEquals(4096, config.getMaxTokens());
    }
}
