package com.example.aiagent.controller;

import com.example.aiagent.agent.ReActAgent;
import com.example.aiagent.memory.AgentMemoryService;
import com.example.aiagent.model.ChatRequest;
import com.example.aiagent.model.ChatResponse;
import com.example.aiagent.service.KafkaEventPublisher;
import com.example.aiagent.service.OllamaService;
import com.example.aiagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final ReActAgent agent;
    private final OllamaService ollamaService;
    private final AgentMemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final KafkaEventPublisher kafkaPublisher;

    public AgentController(ReActAgent agent, OllamaService ollamaService,
                           AgentMemoryService memoryService, ToolRegistry toolRegistry,
                           KafkaEventPublisher kafkaPublisher) {
        this.agent = agent;
        this.ollamaService = ollamaService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.kafkaPublisher = kafkaPublisher;
    }

    @PostMapping("/agent/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        long startTime = System.currentTimeMillis();

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        log.info("Chat request for session {}: {}", sessionId, request.getMessage());
        kafkaPublisher.publishChatEvent(sessionId, "chat_request", request.getMessage());

        String answer = agent.run(request.getMessage(), sessionId);

        long processingTime = System.currentTimeMillis() - startTime;
        List<String> toolsUsed = List.of("weather", "database", "calculator", "news", "rag_search");

        ChatResponse response = new ChatResponse(answer, sessionId, toolsUsed, processingTime);
        kafkaPublisher.publishChatEvent(sessionId, "chat_response", answer);

        log.info("Response sent for session {} in {}ms", sessionId, processingTime);
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
        boolean ollamaAvailable = ollamaService.isAvailable();
        return ResponseEntity.ok(Map.of(
            "status", ollamaAvailable ? "healthy" : "degraded",
            "ollama", ollamaAvailable ? "connected" : "disconnected",
            "tools", toolRegistry.getToolNames(),
            "version", "1.0.0"
        ));
    }
}
