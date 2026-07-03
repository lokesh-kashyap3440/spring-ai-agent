package com.example.aiagent.service;

public interface AiService {
    String chat(String systemPrompt, String userMessage);
    boolean isAvailable();

    /**
     * Returns the number of prompt tokens used in the last {@link #chat} call.
     * Returns 0 by default for implementations that do not track token usage.
     */
    default int getLastPromptTokens() {
        return 0;
    }

    /**
     * Returns the number of completion tokens used in the last {@link #chat} call.
     * Returns 0 by default for implementations that do not track token usage.
     */
    default int getLastCompletionTokens() {
        return 0;
    }
}
