package com.example.aiagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @Test
    void testDefaultValues() {
        AgentConfig config = new AgentConfig();
        assertEquals(6, config.getMaxIterations());
        assertEquals(20, config.getMemorySize());
    }

    @Test
    void testSetters() {
        AgentConfig config = new AgentConfig();
        config.setMaxIterations(5);
        config.setMemorySize(50);
        assertEquals(5, config.getMaxIterations());
        assertEquals(50, config.getMemorySize());
    }
}
