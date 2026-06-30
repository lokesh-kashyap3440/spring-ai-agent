package com.example.aiagent.memory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentMemoryService {

    private static final int MAX_MESSAGES = 50;
    private final JdbcTemplate jdbc;

    public AgentMemoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveMessage(String sessionId, String role, String content) {
        jdbc.update("INSERT INTO conversation_memory (session_id, role, content) VALUES (?, ?, ?)",
                sessionId, role, content);
        trimHistory(sessionId);
    }

    public List<String> getConversationHistory(String sessionId) {
        return jdbc.query(
                "SELECT role || ': ' || content FROM conversation_memory WHERE session_id = ? ORDER BY created_at, id",
                (rs, row) -> rs.getString(1),
                sessionId
        );
    }

    public String getFormattedHistory(String sessionId) {
        List<String> history = getConversationHistory(sessionId);
        if (history.isEmpty()) return "";
        return String.join("\n", history.stream().map(s -> "  " + s).toList());
    }

    public void clearMemory(String sessionId) {
        jdbc.update("DELETE FROM conversation_memory WHERE session_id = ?", sessionId);
    }

    public void saveAgentState(String sessionId, String state) {
        jdbc.update("INSERT INTO conversation_memory (session_id, role, content) VALUES (?, 'state', ?)",
                sessionId, state);
    }

    public String getAgentState(String sessionId) {
        var results = jdbc.query(
                "SELECT content FROM conversation_memory WHERE session_id = ? AND role = 'state' ORDER BY created_at DESC LIMIT 1",
                (rs, row) -> rs.getString(1),
                sessionId
        );
        return results.isEmpty() ? null : results.getFirst();
    }

    private void trimHistory(String sessionId) {
        jdbc.update("""
                DELETE FROM conversation_memory WHERE session_id = ? AND id <= (
                    SELECT id FROM conversation_memory WHERE session_id = ?
                    ORDER BY id DESC OFFSET ? LIMIT 1
                )
                """, sessionId, sessionId, MAX_MESSAGES);
    }
}
