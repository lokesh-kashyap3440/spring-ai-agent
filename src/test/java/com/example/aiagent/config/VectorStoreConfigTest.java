package com.example.aiagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreConfigTest {

    @Test
    void testConfigLoads() {
        PgVectorStoreConfig config = new PgVectorStoreConfig();
        assertNotNull(config);
    }
}
