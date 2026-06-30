package com.example.aiagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NvidiaConfigTest {

    @Test
    void testDefaultValues() {
        NvidiaConfig config = new NvidiaConfig();
        assertEquals("", config.getApiKey());
        assertEquals("meta/llama-3.1-8b-instruct", config.getModel());
        assertEquals("https://integrate.api.nvidia.com/v1", config.getBaseUrl());
        assertEquals(60, config.getTimeout());
        assertEquals(2048, config.getMaxTokens());
        assertEquals(0.7, config.getTemperature());
    }

    @Test
    void testSetters() {
        NvidiaConfig config = new NvidiaConfig();
        config.setApiKey("test-key");
        config.setModel("test-model");
        config.setBaseUrl("https://test.url");
        config.setTimeout(30);
        config.setMaxTokens(1024);
        config.setTemperature(0.5);

        assertEquals("test-key", config.getApiKey());
        assertEquals("test-model", config.getModel());
        assertEquals("https://test.url", config.getBaseUrl());
        assertEquals(30, config.getTimeout());
        assertEquals(1024, config.getMaxTokens());
        assertEquals(0.5, config.getTemperature());
    }
}
