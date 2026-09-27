package com.example.aiagent.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@ConditionalOnProperty(value = "spring.kafka.bootstrap-servers")
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.events:ai-agent-events}")
    private String eventsTopic;

    @Value("${app.kafka.topics.chat:ai-agent-chat}")
    private String chatTopic;

    @Value("${app.kafka.replication-factor:1}")
    private int replicationFactor;

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        // Keep MAX_BLOCK_MS_CONFIG at 2000 to fail fast when broker is unreachable,
        // preventing the producer from hanging indefinitely during send() calls.
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2000);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Production: disable auto topic creation on the client side
        props.put(ProducerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Explicitly creates Kafka topics at startup instead of relying on
     * auto-creation (which is disabled for production safety).
     */
    @Bean
    public NewTopic eventsTopic() {
        return TopicBuilder.name(eventsTopic)
                .partitions(6)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic chatTopic() {
        return TopicBuilder.name(chatTopic)
                .partitions(6)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * Validates Kafka connectivity at startup.
     * In production (fail-fast), throws if Kafka is unreachable.
     * In development, logs a warning and continues (Kafka may start later).
     */
    @Bean
    public AdminClient adminClient() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        AdminClient client = AdminClient.create(props);
        try {
            client.listTopics().names().get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            if (isProductionProfile()) {
                throw new IllegalStateException("Cannot connect to Kafka at " + bootstrapServers, e);
            }
            log.warn("Cannot connect to Kafka at {} (continuing in dev mode, consumers/producers may fail later)", bootstrapServers);
        }
        return client;
    }

    private boolean isProductionProfile() {
        return System.getProperty("spring.profiles.active", "").contains("prod")
                || (System.getenv("SPRING_PROFILES_ACTIVE") != null
                && System.getenv("SPRING_PROFILES_ACTIVE").contains("prod"));
    }
}
