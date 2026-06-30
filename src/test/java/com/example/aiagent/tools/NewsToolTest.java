package com.example.aiagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NewsToolTest {

    private NewsTool tool;

    @BeforeEach
    void setUp() {
        tool = new NewsTool();
    }

    @Test
    void testName() {
        assertEquals("news", tool.getName());
    }

    @Test
    void testDescription() {
        assertTrue(tool.getDescription().contains("news headlines"));
    }

    @Test
    void testFallbackOnError() {
        String result = tool.execute("technology");
        assertTrue(result.contains("Simulated news for 'technology'"));
    }
}
