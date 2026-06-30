package com.example.aiagent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentMemoryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOps;

    @Mock
    private ValueOperations<String, String> valueOps;

    private AgentMemoryService memoryService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        memoryService = new AgentMemoryService(redisTemplate);
    }

    @Test
    void testSaveMessage() {
        memoryService.saveMessage("session-1", "user", "Hello");

        verify(listOps).rightPush("agent:memory:session-1", "user: Hello");
        verify(redisTemplate).expire("agent:memory:session-1", 24, TimeUnit.HOURS);
        verify(listOps).size("agent:memory:session-1");
    }

    @Test
    void testSaveMessageTrimsWhenOver50() {
        when(listOps.size("agent:memory:session-1")).thenReturn(51L);

        memoryService.saveMessage("session-1", "user", "msg");

        verify(listOps).rightPush("agent:memory:session-1", "user: msg");
        verify(listOps).leftPop("agent:memory:session-1");
    }

    @Test
    void testSaveMessageDoesNotTrimWhenUnder50() {
        when(listOps.size("agent:memory:session-1")).thenReturn(10L);

        memoryService.saveMessage("session-1", "user", "msg");

        verify(listOps, never()).leftPop(anyString());
    }

    @Test
    void testGetConversationHistoryReturnsList() {
        when(listOps.size("agent:memory:session-1")).thenReturn(2L);
        when(listOps.range("agent:memory:session-1", 0, -1))
                .thenReturn(List.of("user: Hello", "assistant: Hi"));

        List<String> history = memoryService.getConversationHistory("session-1");

        assertEquals(2, history.size());
        assertEquals("user: Hello", history.get(0));
        assertEquals("assistant: Hi", history.get(1));
    }

    @Test
    void testGetConversationHistoryEmpty() {
        when(listOps.size("agent:memory:session-1")).thenReturn(0L);

        List<String> history = memoryService.getConversationHistory("session-1");

        assertTrue(history.isEmpty());
    }

    @Test
    void testGetConversationHistoryNull() {
        when(listOps.size("agent:memory:session-1")).thenReturn(null);

        List<String> history = memoryService.getConversationHistory("session-1");

        assertTrue(history.isEmpty());
    }

    @Test
    void testGetFormattedHistory() {
        when(listOps.size("agent:memory:session-1")).thenReturn(2L);
        when(listOps.range("agent:memory:session-1", 0, -1))
                .thenReturn(List.of("user: Hello", "assistant: Hi"));

        String formatted = memoryService.getFormattedHistory("session-1");

        assertTrue(formatted.contains("  user: Hello"));
        assertTrue(formatted.contains("  assistant: Hi"));
    }

    @Test
    void testClearMemory() {
        memoryService.clearMemory("session-1");
        verify(redisTemplate).delete("agent:memory:session-1");
    }

    @Test
    void testSaveAgentState() {
        memoryService.saveAgentState("session-1", "{\"state\": \"running\"}");

        verify(valueOps).set("agent:memory:state:session-1", "{\"state\": \"running\"}", 24, TimeUnit.HOURS);
    }

    @Test
    void testGetAgentState() {
        when(valueOps.get("agent:memory:state:session-1")).thenReturn("{\"state\": \"completed\"}");

        String state = memoryService.getAgentState("session-1");

        assertEquals("{\"state\": \"completed\"}", state);
    }

    @Test
    void testGetAgentStateReturnsNullWhenNotFound() {
        when(valueOps.get("agent:memory:state:session-1")).thenReturn(null);

        String state = memoryService.getAgentState("session-1");

        assertNull(state);
    }
}
