package com.example.aiagent.service;

public interface AiService {
    String chat(String systemPrompt, String userMessage);
    boolean isAvailable();
}
