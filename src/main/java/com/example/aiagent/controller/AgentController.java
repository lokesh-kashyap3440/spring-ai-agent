package com.example.aiagent.controller;

import com.example.aiagent.agent.ReActAgent;
import com.example.aiagent.memory.AgentMemoryService;
import com.example.aiagent.model.ChatRequest;
import com.example.aiagent.model.ChatResponse;
import com.example.aiagent.service.KafkaEventPublisher;
import com.example.aiagent.service.AiService;
import com.example.aiagent.tools.ToolRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final ReActAgent agent;
    private final AiService aiService;
    private final AgentMemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final KafkaEventPublisher kafkaPublisher;

    public AgentController(ReActAgent agent, AiService aiService,
                           AgentMemoryService memoryService, ToolRegistry toolRegistry,
                           KafkaEventPublisher kafkaPublisher) {
        this.agent = agent;
        this.aiService = aiService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.kafkaPublisher = kafkaPublisher;
    }

    @PostMapping("/agent/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        Set<String> enabledTools = request.getToolsEnabled() != null ?
                new HashSet<>(request.getToolsEnabled()) : null;

        log.info("Chat request for session {}: {} (tools: {})", sessionId, request.getMessage(),
                enabledTools != null ? enabledTools : "all");
        kafkaPublisher.publishChatEvent(sessionId, "chat_request", request.getMessage());

        ReActAgent.AgentResult result = agent.run(request.getMessage(), sessionId, enabledTools);

        long processingTime = System.currentTimeMillis() - startTime;

        ChatResponse response = new ChatResponse(result.answer(), sessionId, result.toolsUsed(), processingTime);
        kafkaPublisher.publishChatEvent(sessionId, "chat_response", result.answer());

        log.info("Response sent for session {} in {}ms (tools used: {})", sessionId, processingTime, result.toolsUsed());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/agent/session/{sessionId}/history")
    public ResponseEntity<List<String>> getHistory(@PathVariable String sessionId) {
        List<String> history = memoryService.getConversationHistory(sessionId);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/agent/session/{sessionId}")
    public ResponseEntity<Map<String, String>> clearSession(@PathVariable String sessionId) {
        memoryService.clearMemory(sessionId);
        return ResponseEntity.ok(Map.of("status", "cleared", "sessionId", sessionId));
    }

    @GetMapping("/agent/tools")
    public ResponseEntity<Map<String, String>> getTools() {
        return ResponseEntity.ok(toolRegistry.getAllTools().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getDescription()
                )));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean aiAvailable = aiService.isAvailable();
        String provider = aiService.getClass().getSimpleName().replace("Service", "").toLowerCase();
        return ResponseEntity.ok(Map.of(
                "status", aiAvailable ? "healthy" : "degraded",
                "provider", provider,
                "available", aiAvailable,
                "tools", toolRegistry.getToolNames(),
                "version", "2.0.0"
        ));
    }

    @GetMapping("/thread-info")
    public Map<String, Object> threadInfo() {
        Thread t = Thread.currentThread();
        return Map.of(
                "name", t.getName(),
                "isVirtual", t.isVirtual(),
                "threadGroup", t.getThreadGroup() != null ? t.getThreadGroup().getName() : null,
                "priority", t.getPriority(),
                "state", t.getState().toString()
        );
    }
}
