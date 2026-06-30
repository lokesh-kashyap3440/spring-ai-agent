package com.example.aiagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseToolTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private DatabaseTool tool;

    @BeforeEach
    void setUp() {
        tool = new DatabaseTool(redisTemplate);
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
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("db:what is spring boot?")).thenReturn(null);
        String result = tool.execute("what is spring boot?");
        assertTrue(result.startsWith("Found:"));
        assertTrue(result.contains("Spring Boot"));
        verify(valueOps).set(eq("db:what is spring boot?"), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void testCachedResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("db:what is kafka?")).thenReturn("Apache Kafka is a distributed event streaming platform.");
        String result = tool.execute("what is kafka?");
        assertTrue(result.startsWith("From cache:"));
        assertTrue(result.contains("Kafka"));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void testUnknownQuery() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("db:unknown topic")).thenReturn(null);
        String result = tool.execute("unknown topic");
        assertTrue(result.contains("No specific information found"));
        assertTrue(result.contains("Available topics"));
    }

    @Test
    void testCaseInsensitiveQuery() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("db:redis info")).thenReturn(null);
        String result = tool.execute("Redis info");
        assertTrue(result.contains("Found:"));
        assertTrue(result.contains("Redis"));
    }

    @Test
    void testRedisErrorReturnsFallback() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Connection refused"));
        String result = tool.execute("spring boot");
        assertTrue(result.contains("Database error"));
    }
}
