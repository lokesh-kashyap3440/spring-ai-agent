package com.example.aiagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class NoOpKafkaEventPublisher implements KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpKafkaEventPublisher.class);

    @Override
    public void publishChatEvent(String sessionId, String event, String details) {
        log.debug("Kafka not configured — skipping chat event: {} for session: {}", event, sessionId);
    }

    @Override
    public void publishAgentEvent(String sessionId, String type, String content) {
        log.debug("Kafka not configured — skipping agent event: {} for session: {}", type, sessionId);
    }
}
