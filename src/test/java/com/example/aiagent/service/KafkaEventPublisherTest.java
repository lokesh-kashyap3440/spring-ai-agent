package com.example.aiagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaEventPublisher publisher;

    @BeforeEach
    void setUp() throws Exception {
        publisher = new KafkaEventPublisher(kafkaTemplate);
        var eventsField = KafkaEventPublisher.class.getDeclaredField("eventsTopic");
        eventsField.setAccessible(true);
        eventsField.set(publisher, "ai-agent-events");
        var chatField = KafkaEventPublisher.class.getDeclaredField("chatTopic");
        chatField.setAccessible(true);
        chatField.set(publisher, "ai-agent-chat");
    }

    @Test
    void testPublishChatEvent() {
        publisher.publishChatEvent("session-1", "chat_request", "Hello");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("ai-agent-chat"), eq("session-1"), captor.capture());

        Map<String, Object> payload = captor.getValue();
        assertEquals("session-1", payload.get("sessionId"));
        assertEquals("chat_request", payload.get("event"));
        assertEquals("Hello", payload.get("details"));
        assertNotNull(payload.get("timestamp"));
    }

    @Test
    void testPublishAgentEvent() {
        publisher.publishAgentEvent("session-1", "tool_call", "weather -> result");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(eq("ai-agent-events"), eq("session-1"), captor.capture());

        Map<String, Object> payload = captor.getValue();
        assertEquals("session-1", payload.get("sessionId"));
        assertEquals("tool_call", payload.get("type"));
        assertEquals("weather -> result", payload.get("content"));
        assertNotNull(payload.get("timestamp"));
    }

    @Test
    void testPublishChatEventHandlesExceptionGracefully() {
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString(), any());

        assertDoesNotThrow(() -> publisher.publishChatEvent("session-1", "event", "details"));
    }

    @Test
    void testPublishAgentEventHandlesExceptionGracefully() {
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString(), any());

        assertDoesNotThrow(() -> publisher.publishAgentEvent("session-1", "type", "content"));
    }
}
