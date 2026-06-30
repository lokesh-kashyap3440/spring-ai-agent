package com.example.aiagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class VectorStoreConfigTest {

    @Test
    void testJedisConnectionFactoryDefaultValues() {
        VectorStoreConfig config = new VectorStoreConfig();
        ReflectionTestUtils.setField(config, "redisHost", "localhost");
        ReflectionTestUtils.setField(config, "redisPort", 6380);
        ReflectionTestUtils.setField(config, "redisPassword", "");

        JedisConnectionFactory factory = config.jedisConnectionFactory();
        assertNotNull(factory);
        assertEquals("localhost", factory.getHostName());
        assertEquals(6380, factory.getPort());
    }

    @Test
    void testJedisConnectionFactoryWithPassword() {
        VectorStoreConfig config = new VectorStoreConfig();
        ReflectionTestUtils.setField(config, "redisHost", "redis.example.com");
        ReflectionTestUtils.setField(config, "redisPort", 6379);
        ReflectionTestUtils.setField(config, "redisPassword", "secret");

        JedisConnectionFactory factory = config.jedisConnectionFactory();
        assertNotNull(factory);
        assertEquals("redis.example.com", factory.getHostName());
        assertEquals(6379, factory.getPort());
    }

    @Test
    void testJedisConnectionFactoryCustomValues() {
        VectorStoreConfig config = new VectorStoreConfig();
        ReflectionTestUtils.setField(config, "redisHost", "myredis");
        ReflectionTestUtils.setField(config, "redisPort", 6380);
        ReflectionTestUtils.setField(config, "redisPassword", null);

        JedisConnectionFactory factory = config.jedisConnectionFactory();
        assertNotNull(factory);
        assertEquals("myredis", factory.getHostName());
    }
}
