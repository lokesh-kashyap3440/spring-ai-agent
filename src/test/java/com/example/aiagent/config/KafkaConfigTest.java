package com.example.aiagent.config;

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

        ProducerFactory<String, Object> factory = config.producerFactory();
        assertNotNull(factory);
        assertInstanceOf(DefaultKafkaProducerFactory.class, factory);

        DefaultKafkaProducerFactory<String, Object> defaultFactory =
                (DefaultKafkaProducerFactory<String, Object>) factory;
        Map<String, Object> props = defaultFactory.getConfigurationProperties();
        assertEquals("localhost:9092", props.get("bootstrap.servers"));
        assertEquals("2000", props.get("max.block.ms").toString());
        assertEquals("0", props.get("retries").toString());
        assertEquals("2000", props.get("request.timeout.ms").toString());
    }

    @Test
    void testKafkaTemplateCreation() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");

        KafkaTemplate<String, Object> template = config.kafkaTemplate();
        assertNotNull(template);
    }
}
