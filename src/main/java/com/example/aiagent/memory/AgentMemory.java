package com.example.aiagent.memory;

import java.util.List;

public interface AgentMemory {
    void saveMessage(String sessionId, String role, String content);
    List<String> getConversationHistory(String sessionId);
    String getFormattedHistory(String sessionId);
    void clearMemory(String sessionId);
    void saveAgentState(String sessionId, String state);
    String getAgentState(String sessionId);
}
