package com.example.aiagent.memory;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AgentMemoryService {

    private static final String MEMORY_PREFIX = "agent:memory:";
    private static final int MEMORY_TTL_HOURS = 24;
    private final StringRedisTemplate redisTemplate;

    public AgentMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveMessage(String sessionId, String role, String content) {
        String key = MEMORY_PREFIX + sessionId;
        String entry = role + ": " + content;
        redisTemplate.opsForList().rightPush(key, entry);
        redisTemplate.expire(key, MEMORY_TTL_HOURS, TimeUnit.HOURS);

        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > 50) {
            redisTemplate.opsForList().leftPop(key);
        }
    }

    public List<String> getConversationHistory(String sessionId) {
        String key = MEMORY_PREFIX + sessionId;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return List.of();
        }
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    public String getFormattedHistory(String sessionId) {
        List<String> history = getConversationHistory(sessionId);
        return history.stream()
                .map(s -> "  " + s)
                .collect(Collectors.joining("\n"));
    }

    public void clearMemory(String sessionId) {
        String key = MEMORY_PREFIX + sessionId;
        redisTemplate.delete(key);
    }

    public void saveAgentState(String sessionId, String state) {
        String key = MEMORY_PREFIX + "state:" + sessionId;
        redisTemplate.opsForValue().set(key, state, MEMORY_TTL_HOURS, TimeUnit.HOURS);
    }

    public String getAgentState(String sessionId) {
        String key = MEMORY_PREFIX + "state:" + sessionId;
        return redisTemplate.opsForValue().get(key);
    }
}
