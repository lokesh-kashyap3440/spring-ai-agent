package com.example.aiagent.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatResponseTest {

    @Test
    void testDefaultConstructorSetsTimestamp() {
        ChatResponse response = new ChatResponse();
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testParameterizedConstructor() {
        ChatResponse response = new ChatResponse("answer", "session-1", List.of("tool1"), 100L);
        assertEquals("answer", response.getAnswer());
        assertEquals("session-1", response.getSessionId());
        assertEquals(List.of("tool1"), response.getToolsUsed());
        assertEquals(100L, response.getProcessingTimeMs());
        assertNotNull(response.getTimestamp());
    }

    @Test
    void testSettersAndGetters() {
        ChatResponse response = new ChatResponse();
        response.setAnswer("final answer");
        response.setSessionId("sid");
        response.setToolsUsed(List.of("a", "b"));
        response.setProcessingTimeMs(500L);
        Instant now = Instant.now();
        response.setTimestamp(now);

        assertEquals("final answer", response.getAnswer());
        assertEquals("sid", response.getSessionId());
        assertEquals(List.of("a", "b"), response.getToolsUsed());
        assertEquals(500L, response.getProcessingTimeMs());
        assertEquals(now, response.getTimestamp());
    }
}
