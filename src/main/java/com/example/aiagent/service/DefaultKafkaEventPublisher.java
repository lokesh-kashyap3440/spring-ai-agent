package com.example.aiagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(value = "spring.kafka.bootstrap-servers")
public class DefaultKafkaEventPublisher implements KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DefaultKafkaEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.events:ai-agent-events}")
    private String eventsTopic;

    @Value("${app.kafka.topics.chat:ai-agent-chat}")
    private String chatTopic;

    public DefaultKafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishChatEvent(String sessionId, String event, String details) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("event", event);
            payload.put("details", details);
            payload.put("timestamp", Instant.now().toString());

            kafkaTemplate.send(chatTopic, sessionId, payload);
            log.debug("Published chat event: {} for session: {}", event, sessionId);
        } catch (Exception e) {
            log.warn("Failed to publish Kafka event: {}", e.getMessage());
        }
    }

    @Override
    public void publishAgentEvent(String sessionId, String type, String content) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("sessionId", sessionId);
            payload.put("type", type);
            payload.put("content", content);
            payload.put("timestamp", Instant.now().toString());

            kafkaTemplate.send(eventsTopic, sessionId, payload);
            log.debug("Published agent event: {} for session: {}", type, sessionId);
        } catch (Exception e) {
            log.warn("Failed to publish Kafka event: {}", e.getMessage());
        }
    }
}
