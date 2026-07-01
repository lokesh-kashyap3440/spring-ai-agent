package com.example.aiagent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ChatRequest {

    @NotBlank(message = "Message cannot be empty")
    @Size(min = 1, max = 10000, message = "Message must be between 1 and 10000 characters")
    private String message;

    private String sessionId;

    private List<String> toolsEnabled;

    public ChatRequest() {
    }

    public ChatRequest(String message, String sessionId) {
        this.message = message;
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<String> getToolsEnabled() {
        return toolsEnabled;
    }

    public void setToolsEnabled(List<String> toolsEnabled) {
        this.toolsEnabled = toolsEnabled;
    }
}
