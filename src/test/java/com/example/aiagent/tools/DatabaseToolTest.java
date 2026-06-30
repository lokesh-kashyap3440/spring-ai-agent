package com.example.aiagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseToolTest {

    private DatabaseTool tool;

    @BeforeEach
    void setUp() {
        tool = new DatabaseTool();
    }

    @Test
    void testName() {
        assertEquals("database", tool.getName());
    }

    @Test
    void testDescription() {
        assertTrue(tool.getDescription().contains("knowledge base"));
    }

    @Test
    void testExactMatchQuery() {
        String result = tool.execute("what is spring boot?");
        assertTrue(result.startsWith("Found:"));
        assertTrue(result.contains("Spring Boot"));
    }

    @Test
    void testUnknownQuery() {
        String result = tool.execute("unknown topic");
        assertTrue(result.contains("No specific information found"));
        assertTrue(result.contains("Available topics"));
    }

    @Test
    void testCaseInsensitiveQuery() {
        String result = tool.execute("Redis info");
        assertTrue(result.contains("Found:"));
        assertTrue(result.contains("Redis"));
    }

    @Test
    void testEmptyQuery() {
        String result = tool.execute("");
        assertTrue(result.contains("No specific information found"));
    }
}
