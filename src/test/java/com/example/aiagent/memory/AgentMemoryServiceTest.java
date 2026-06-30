package com.example.aiagent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentMemoryServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private AgentMemoryService memoryService;

    @BeforeEach
    void setUp() {
        memoryService = new AgentMemoryService(jdbc);
    }

    @Test
    void testSaveMessage() {
        memoryService.saveMessage("session-1", "user", "Hello");
        verify(jdbc).update(
                eq("INSERT INTO conversation_memory (session_id, role, content) VALUES (?, ?, ?)"),
                eq("session-1"), eq("user"), eq("Hello"));
    }

    @Test
    void testGetConversationHistory() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("session-1")))
                .thenReturn(List.of("user: Hello", "assistant: Hi"));

        List<String> history = memoryService.getConversationHistory("session-1");
        assertEquals(2, history.size());
        assertEquals("user: Hello", history.get(0));
    }

    @Test
    void testGetConversationHistoryEmpty() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("session-1")))
                .thenReturn(List.of());

        List<String> history = memoryService.getConversationHistory("session-1");
        assertTrue(history.isEmpty());
    }

    @Test
    void testGetFormattedHistory() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("session-1")))
                .thenReturn(List.of("user: Hello", "assistant: Hi"));

        String formatted = memoryService.getFormattedHistory("session-1");
        assertTrue(formatted.contains("  user: Hello"));
        assertTrue(formatted.contains("  assistant: Hi"));
    }

    @Test
    void testClearMemory() {
        memoryService.clearMemory("session-1");
        verify(jdbc).update("DELETE FROM conversation_memory WHERE session_id = ?", "session-1");
    }

    @Test
    void testSaveAgentState() {
        memoryService.saveAgentState("session-1", "running");
        verify(jdbc).update(
                eq("INSERT INTO conversation_memory (session_id, role, content) VALUES (?, 'state', ?)"),
                eq("session-1"), eq("running"));
    }

    @Test
    void testGetAgentState() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("session-1")))
                .thenReturn(List.of("completed"));
        String state = memoryService.getAgentState("session-1");
        assertEquals("completed", state);
    }

    @Test
    void testGetAgentStateReturnsNullWhenNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("session-1")))
                .thenReturn(List.of());
        String state = memoryService.getAgentState("session-1");
        assertNull(state);
    }
}
