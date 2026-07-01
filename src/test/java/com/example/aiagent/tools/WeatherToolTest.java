package com.example.aiagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

class WeatherToolTest {

    private WeatherTool tool;

    @BeforeEach
    void setUp() {
        tool = new WeatherTool(new RestTemplate());
    }

    @Test
    void testName() {
        assertEquals("weather", tool.getName());
    }

    @Test
    void testDescription() {
        assertTrue(tool.getDescription().contains("current weather"));
    }

    @Test
    void testFallbackOrActualResponse() {
        String result = tool.execute("London");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }
}
