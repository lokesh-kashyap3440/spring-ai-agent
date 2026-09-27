package com.example.aiagent.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class KafkaConfigTest {

    @Test
    void testProducerFactoryCreatesWithCorrectProperties() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "eventsTopic", "ai-agent-events");
        ReflectionTestUtils.setField(config, "chatTopic", "ai-agent-chat");
        ReflectionTestUtils.setField(config, "replicationFactor", 1);

        ProducerFactory<String, Object> factory = config.producerFactory();
        assertNotNull(factory);
        assertInstanceOf(DefaultKafkaProducerFactory.class, factory);

        DefaultKafkaProducerFactory<String, Object> defaultFactory =
                (DefaultKafkaProducerFactory<String, Object>) factory;
        Map<String, Object> props = defaultFactory.getConfigurationProperties();
        assertEquals("localhost:9092", props.get("bootstrap.servers"));
        assertEquals("2000", props.get("max.block.ms").toString());
        assertEquals("3", props.get("retries").toString());
        assertEquals("2000", props.get("request.timeout.ms").toString());
        assertEquals(false, props.get("allow.auto.create.topics"));
    }

    @Test
    void testKafkaTemplateCreation() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "eventsTopic", "ai-agent-events");
        ReflectionTestUtils.setField(config, "chatTopic", "ai-agent-chat");
        ReflectionTestUtils.setField(config, "replicationFactor", 1);

        KafkaTemplate<String, Object> template = config.kafkaTemplate();
        assertNotNull(template);
    }

    @Test
    void testTopicCreation() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "eventsTopic", "ai-agent-events");
        ReflectionTestUtils.setField(config, "chatTopic", "ai-agent-chat");
        ReflectionTestUtils.setField(config, "replicationFactor", 3);

        NewTopic events = config.eventsTopic();
        assertEquals("ai-agent-events", events.name());
        assertEquals(6, events.numPartitions());

        NewTopic chat = config.chatTopic();
        assertEquals("ai-agent-chat", chat.name());
        assertEquals(6, chat.numPartitions());
    }
}
