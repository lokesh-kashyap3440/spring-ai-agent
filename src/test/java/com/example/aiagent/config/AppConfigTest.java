package com.example.aiagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void testRestTemplateBean() {
        AppConfig appConfig = new AppConfig();
        RestTemplate restTemplate = appConfig.restTemplate();
        assertNotNull(restTemplate);
    }

    @Test
    void testObjectMapperBeanWithJavaTimeModule() {
        AppConfig appConfig = new AppConfig();
        ObjectMapper mapper = appConfig.objectMapper();
        assertNotNull(mapper);
        assertDoesNotThrow(() -> mapper.writeValueAsString(Instant.now()));
    }

    @Test
    void testObjectMapperSerializesInstant() throws Exception {
        AppConfig appConfig = new AppConfig();
        ObjectMapper mapper = appConfig.objectMapper();
        Instant now = Instant.now();
        String json = mapper.writeValueAsString(now);
        assertNotNull(json);
        Instant deserialized = mapper.readValue(json, Instant.class);
        assertEquals(now, deserialized);
    }
}
