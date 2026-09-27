package com.example.aiagent.service;

import java.time.Instant;
import java.util.Map;

public interface KafkaEventPublisher {

    void publishChatEvent(String sessionId, String event, String details);

    void publishAgentEvent(String sessionId, String type, String content);
}
