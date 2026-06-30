package com.example.aiagent.controller;

import com.example.aiagent.agent.ReActAgent;
import com.example.aiagent.memory.AgentMemoryService;
import com.example.aiagent.model.ChatRequest;
import com.example.aiagent.service.KafkaEventPublisher;
import com.example.aiagent.service.AiService;
import com.example.aiagent.tools.Tool;
import com.example.aiagent.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private ReActAgent agent;

    @Mock
    private AiService aiService;

    @Mock
    private AgentMemoryService memoryService;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private KafkaEventPublisher kafkaPublisher;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        AgentController controller = new AgentController(agent, aiService, memoryService, toolRegistry, kafkaPublisher);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testChatEndpoint() throws Exception {
        when(agent.run(anyString(), anyString(), any()))
                .thenReturn(new ReActAgent.AgentResult("Hello!", List.of()));

        ChatRequest request = new ChatRequest("Hi", null);

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Hello!"));
    }

    @Test
    void testChatEndpointWithSessionId() throws Exception {
        when(agent.run(anyString(), eq("session-1"), any()))
                .thenReturn(new ReActAgent.AgentResult("Response", List.of("weather")));

        ChatRequest request = new ChatRequest("Weather?", "session-1");

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Response"))
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.toolsUsed[0]").value("weather"));
    }

    @Test
    void testChatEndpointEmptyMessageReturnsBadRequest() throws Exception {
        ChatRequest request = new ChatRequest("", null);

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetHistory() throws Exception {
        when(memoryService.getConversationHistory("session-1"))
                .thenReturn(List.of("user: Hi", "assistant: Hello"));

        mockMvc.perform(get("/api/agent/session/session-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("user: Hi"))
                .andExpect(jsonPath("$[1]").value("assistant: Hello"));
    }

    @Test
    void testClearSession() throws Exception {
        mockMvc.perform(delete("/api/agent/session/session-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cleared"))
                .andExpect(jsonPath("$.sessionId").value("session-1"));
    }

    @Test
    void testGetTools() throws Exception {
        Tool tool = new Tool() {
            @Override
            public String getName() { return "weather"; }
            @Override
            public String getDescription() { return "Get weather"; }
            @Override
            public String execute(String input) { return "sunny"; }
        };
        when(toolRegistry.getAllTools()).thenReturn(Map.of("weather", tool));

        mockMvc.perform(get("/api/agent/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weather").value("Get weather"));
    }

    @Test
    void testHealthEndpoint() throws Exception {
        when(aiService.isAvailable()).thenReturn(true);
        when(toolRegistry.getToolNames()).thenReturn("weather, calculator");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("healthy"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.version").value("2.0.0"));
    }

    @Test
    void testHealthDegraded() throws Exception {
        when(aiService.isAvailable()).thenReturn(false);
        when(toolRegistry.getToolNames()).thenReturn("");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.available").value(false));
    }
}
