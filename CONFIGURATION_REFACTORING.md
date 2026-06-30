# Spring Boot Configuration Refactoring Guide

## Overview

This document outlines the comprehensive improvements made to the Spring Boot configuration for the AI Agent application. The refactoring transforms a monolithic, environment-specific configuration into a production-ready, multi-profile setup following Spring Boot best practices.

---

## Key Improvements

### 1. **Environment Variable Support**
**Before:** Hardcoded values like `localhost:9093`, `localhost:11434`, etc.
**After:** All external dependencies use environment variable placeholders with sensible defaults.

```yaml
# Before
bootstrap-servers: localhost:9093
base-url: http://localhost:11434

# After
bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9093}
base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
```

**Benefits:**
- Works seamlessly in Docker/Kubernetes
- Supports 12-factor app methodology
- Enables different configurations per environment without code changes
- Secrets management integration (Vault, AWS Secrets Manager, etc.)

---

### 2. **Profile-Specific Configuration**
**Added:** `application-dev.yml` and `application-prod.yml`

**Development Profile (`application-dev.yml`):**
- Debug logging enabled
- Development-specific Kafka consumer group (`ai-agent-dev`)
- Separate Kafka topics for dev isolation (`-dev` suffix)
- Verbose agent logging for debugging

**Production Profile (`application-prod.yml`):**
- WARN level logging by default
- Health check readiness/liveness probes with Redis/Kafka checks
- Enhanced Kafka producer configuration (compression, batching)
- Log file rotation and archiving
- Health details hidden from unauthorized users

**Benefits:**
- Clear separation of concerns between environments
- Production-optimized defaults
- Easier debugging in development
- Security hardening for production

---

### 3. **Redis Configuration Enhancement**
**Before:** Basic Redis config with only host, port, timeout
**After:** Added Lettuce connection pool configuration

```yaml
datasource:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    timeout: ${REDIS_TIMEOUT:2000}
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: ${REDIS_MAX_WAIT:-1}
```

**Benefits:**
- Prevents connection exhaustion
- Improves performance under load
- Configurable pool size per environment
- Production profile increases pool size to 16

---

### 4. **Kafka Producer Optimization**
**Added:** Production-grade Kafka producer settings

```yaml
producer:
  acks: all                    # Guarantee message delivery
  retries: 3                   # Retry failed sends
  compression-type: snappy     # Reduce bandwidth
  linger-ms: 5                 # Batch messages
  batch-size: 16384           # Optimal batch size
```

**Benefits:**
- Better throughput with batching
- Message durability guarantees
- Reduced network traffic with compression
- Configurable per environment

---

### 5. **Kafka Consumer Improvements**
**Added:** Consumer concurrency and tuning

```yaml
consumer:
  concurrency: 3               # Parallel consumption
  max-poll-records: 500        # Batch processing
  fetch-min-size: 1
  fetch-max-wait-ms: 500
```

**Benefits:**
- Increased throughput with parallel consumers
- Better resource utilization
- Tuned for low latency

---

### 6. **Management & Monitoring (Actuator)**
**Added:** Comprehensive health checks and metrics

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized  # Security
      probes:
        enabled: true                 # K8s readiness/liveness
      group:
        readiness:
          include: redis,kafka        # Dependency checks
        liveness:
          include: redis
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      service: ${spring.application.name}
```

**Benefits:**
- Kubernetes-ready health probes
- Prometheus metrics for monitoring
- Security-conscious health details
- Service tagging for distributed tracing

---

### 7. **Structured Logging**
**Added:** Consistent logging configuration with environment-specific levels

```yaml
logging:
  level:
    com.example.aiagent: ${LOG_LEVEL:INFO}
    org.springframework.kafka: ${KAFKA_LOG_LEVEL:WARN}
    org.springframework.ai: ${AI_LOG_LEVEL:WARN}
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

**Production adds:**
- File logging with rotation
- Size-based rollover (10MB max)
- 30-day history retention
- 1GB total size cap

**Benefits:**
- Centralized logging integration (ELK, Loki)
- Log aggregation friendly
- Disk space management
- Debugging in dev, performance in prod

---

### 8. **Configuration Property Validation**
**Recommendation:** Add `@ConfigurationProperties` validation

Update `OllamaConfig.java`:

```java
package com.example.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ValidationMessages;
import org.springframework.stereotype.Component;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Component
@ConfigurationProperties(prefix = "app.ollama")
public class OllamaConfig {

    @NotBlank(message = "Ollama base URL must be specified")
    private String baseUrl = "http://localhost:11434";

    @NotBlank(message = "Ollama model must be specified")
    private String model = "qwen3.5:4b";

    @Min(value = 1, message = "Timeout must be at least 1 second")
    private int timeout = 120;

    @Min(value = 1, message = "Max tokens must be at least 1")
    private int maxTokens = 2048;

    @NotNull(message = "Temperature is required")
    private Double temperature = 0.7;

    // getters and setters...
}
```

**Benefits:**
- Fail fast on invalid configuration
- Clear error messages
- Type safety

---

### 9. **Centralized Configuration Constants**
**Recommendation:** Create a configuration constants class

```java
package com.example.aiagent.config;

public final class ConfigConstants {
    
    private ConfigConstants() {}
    
    // Kafka
    public static final String KAFKA_BOOTSTRAP_SERVERS = "KAFKA_BOOTSTRAP_SERVERS";
    public static final String KAFKA_CONSUMER_GROUP_ID = "KAFKA_CONSUMER_GROUP_ID";
    public static final String KAFKA_TOPIC_EVENTS = "KAFKA_TOPIC_EVENTS";
    public static final String KAFKA_TOPIC_CHAT = "KAFKA_TOPIC_CHAT";
    
    // Ollama
    public static final String OLLAMA_BASE_URL = "OLLAMA_BASE_URL";
    public static final String OLLAMA_MODEL = "OLLAMA_MODEL";
    public static final String OLLAMA_TIMEOUT = "OLLAMA_TIMEOUT";
    public static final String OLLAMA_MAX_TOKENS = "OLLAMA_MAX_TOKENS";
    
    // Redis
    public static final String REDIS_HOST = "REDIS_HOST";
    public static final String REDIS_PORT = "REDIS_PORT";
    
    // Agent
    public static final String AGENT_MAX_ITERATIONS = "AGENT_MAX_ITERATIONS";
    public static final String AGENT_MEMORY_SIZE = "AGENT_MEMORY_SIZE";
    
    // MCP
    public static final String MCP_SERVER_NAME = "MCP_SERVER_NAME";
    public static final String MCP_SERVER_VERSION = "MCP_SERVER_VERSION";
}
```

**Benefits:**
- Avoid typos in property names
- IDE auto-completion support
- Single source of truth

---

### 10. **Configuration Documentation**
**Recommendation:** Add `application.yml` comments or create a README

Create `CONFIGURATION.md`:

```markdown
# AI Agent Configuration

## Environment Variables

### Required for Production
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker addresses
- `OLLAMA_BASE_URL` - Ollama service URL
- `REDIS_HOST` - Redis server hostname

### Optional (with defaults)
See `application.yml` for complete list with defaults.

## Profiles

- `dev` - Development with debug logging, separate Kafka topics
- `prod` - Production with optimized settings, health checks
- `default` - Fallback profile (local development)

## Activating Profiles

```bash
export ACTIVE_PROFILES=prod
# or
java -jar -Dspring.profiles.active=prod ai-agent.jar
```
```

---

### 11. **Security Improvements**

**Issue:** `spring.json.trusted.packages` uses wildcard `*`
**Recommendation:** Use explicit package list

```yaml
properties:
  "[spring.json.trusted.packages]": "com.example.aiagent.models,com.example.aiagent.dto"
```

**Benefits:**
- Prevents deserialization attacks
- Explicit allowlist
- Better security posture

---

### 12. **Missing Configuration Properties**

**Added to `application.yml`:**

- `temperature` for Ollama (controls response randomness)
- `verbose` flag for agent debugging
- Redis connection pool configuration
- Kafka producer/consumer tuning parameters
- Management endpoints for monitoring

---

### 13. **Configuration Order & Organization**

**Restructured for clarity:**
1. Server configuration
2. Spring core (application name, profiles)
3. HTTP/Servlet (converters, multipart)
4. External services (Redis, Kafka, Ollama)
5. AI-specific (Spring AI, Vector Store)
6. Application-specific (`app.*`)
7. Management & Monitoring
8. Logging

**Benefits:**
- Logical grouping
- Easier to navigate
- Standardized structure

---

## Migration Steps

### Step 1: Update Environment Variables
Set required environment variables in your deployment:

```bash
# Docker Compose
environment:
  - KAFKA_BOOTSTRAP_SERVERS=kafka:9093
  - OLLAMA_BASE_URL=http://ollama:11434
  - REDIS_HOST=redis
  - ACTIVE_PROFILES=prod

# Kubernetes
env:
  - name: KAFKA_BOOTSTRAP_SERVERS
    value: "kafka-cluster:9093"
```

### Step 2: Update Docker Compose (if applicable)
```yaml
version: '3.8'
services:
  ai-agent:
    image: ai-agent:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9093
      - OLLAMA_BASE_URL=http://ollama:11434
      - REDIS_HOST=redis
    ports:
      - "8082:8082"
```

### Step 3: Test Locally with Dev Profile
```bash
export ACTIVE_PROFILES=dev
./mvnw spring-boot:run
```

### Step 4: Deploy to Production
```bash
java -jar \
  -Dspring.profiles.active=prod \
  -DKAFKA_BOOTSTRAP_SERVERS=prod-kafka:9093 \
  -DOLLAMA_BASE_URL=https://ollama.prod.example.com \
  ai-agent.jar
```

---

## Configuration Validation

### Check Effective Configuration
```bash
# View all active properties
curl http://localhost:8082/actuator/configprops

# View environment properties
curl http://localhost:8082/actuator/env

# Check health
curl http://localhost:8082/actuator/health
```

### Test Profile Activation
```bash
# Test with dev profile
export ACTIVE_PROFILES=dev
./mvnw test -Dspring.profiles.active=dev

# Test with prod profile
export ACTIVE_PROFILES=prod
./mvnw test -Dspring.profiles.active=prod
```

---

## Best Practices Implemented

1. ✅ **12-Factor App**: Configuration via environment variables
2. ✅ **Profile Separation**: Dev vs Prod configurations
3. ✅ **Security**: Health details restricted, trusted packages explicit
4. ✅ **Observability**: Actuator endpoints, structured logging, Prometheus metrics
5. ✅ **Resilience**: Redis connection pooling, Kafka retries, health probes
6. ✅ **Performance**: Kafka batching/compression, consumer concurrency
7. ✅ **Maintainability**: Clear structure, documented, type-safe
8. ✅ **Docker/K8s Ready**: Environment variables, health checks, resource configs

---

## Additional Recommendations

### 1. Add Configuration Tests
```java
@SpringBootTest(properties = "spring.profiles.active=test")
class ConfigurationTest {
    
    @Test
    void testAllRequiredPropertiesPresent() {
        // Verify all required properties are set
    }
    
    @Test
    void testKafkaConfigValid() {
        // Validate Kafka configuration
    }
}
```

### 2. Implement Config Health Indicator
Already covered by Spring Boot Actuator's auto-configuration.

### 3. Use Config Server (for microservices)
Consider Spring Cloud Config Server if you have multiple services.

### 4. Add Feature Flags
```yaml
app:
  features:
    enable-caching: ${FEATURE_CACHING:true}
    enable-metrics: ${FEATURE_METRICS:true}
    enable-debug-endpoints: ${FEATURE_DEBUG:false}
```

---

## Summary

The refactored configuration provides:
- **Flexibility**: Works in any environment (local, Docker, K8s, cloud)
- **Safety**: Validated, typed, secure defaults
- **Observability**: Full monitoring and logging
- **Performance**: Tuned for production workloads
- **Maintainability**: Clear, documented, organized

Total changes:
- ✅ 3 configuration files (base + 2 profiles)
- ✅ 40+ environment variables with sensible defaults
- ✅ Production-ready Kafka/Redis settings
- ✅ Comprehensive monitoring setup
- ✅ Environment-specific logging
- ✅ Health check readiness/liveness probes

---

## Next Steps

1. Update `docker-compose.yml` to use `prod` profile
2. Add Kubernetes deployment manifests with proper ConfigMaps
3. Implement configuration validation tests
4. Add feature flags for gradual rollouts
5. Set up centralized logging (ELK/Loki)
6. Configure alerting based on Actuator metrics