package com.example.aiagent.model;

import java.time.Instant;
import java.util.List;

public class ChatResponse {

    private String answer;
    private String sessionId;
    private List<String> toolsUsed;
    private long processingTimeMs;
    private Instant timestamp;

    public ChatResponse() {
        this.timestamp = Instant.now();
    }

    public ChatResponse(String answer, String sessionId, List<String> toolsUsed, long processingTimeMs) {
        this.answer = answer;
        this.sessionId = sessionId;
        this.toolsUsed = toolsUsed;
        this.processingTimeMs = processingTimeMs;
        this.timestamp = Instant.now();
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<String> getToolsUsed() {
        return toolsUsed;
    }

    public void setToolsUsed(List<String> toolsUsed) {
        this.toolsUsed = toolsUsed;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
