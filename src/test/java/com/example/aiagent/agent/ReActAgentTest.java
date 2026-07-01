package com.example.aiagent.agent;

import com.example.aiagent.config.AgentConfig;
import com.example.aiagent.memory.AgentMemoryService;
import com.example.aiagent.service.DocumentIngestionService;
import com.example.aiagent.service.KafkaEventPublisher;
import com.example.aiagent.service.AiService;
import com.example.aiagent.tools.Tool;
import com.example.aiagent.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReActAgentTest {

    @Mock
    private AiService aiService;

    @Mock
    private AgentMemoryService memoryService;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private KafkaEventPublisher kafkaPublisher;

    @Mock
    private DocumentIngestionService ingestionService;

    private AgentConfig agentConfig;
    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        agentConfig = new AgentConfig();
        agentConfig.setMaxIterations(10);
        lenient().when(memoryService.getFormattedHistory(anyString())).thenReturn("");
        lenient().doNothing().when(memoryService).saveMessage(anyString(), anyString(), anyString());
        lenient().doNothing().when(kafkaPublisher).publishAgentEvent(anyString(), anyString(), anyString());
        lenient().when(toolRegistry.getToolDescriptions(any())).thenReturn("");
        lenient().when(ingestionService.search(anyString(), anyInt())).thenReturn(List.of());
        lenient().when(toolRegistry.isToolEnabled(eq("rag_search"), any())).thenReturn(true);
        Tool ragTool = mock(Tool.class);
        lenient().when(ragTool.execute(anyString())).thenReturn("No results found");
        lenient().when(toolRegistry.getTool("rag_search")).thenReturn(ragTool);
        agent = new ReActAgent(aiService, memoryService, toolRegistry, agentConfig, kafkaPublisher, ingestionService);
    }

    @Test
    void testFinalAnswerDirectly() {
        when(aiService.chat(anyString(), anyString())).thenReturn("""
                Thought: I know the answer.
                Final Answer: Paris is the capital of France.
                """);

        ReActAgent.AgentResult result = agent.run("What is the capital of France?", "session-1", Set.of("weather"));

        assertEquals("Paris is the capital of France.", result.answer());
        assertTrue(result.toolsUsed().isEmpty());
        verify(memoryService).saveMessage("session-1", "user", "What is the capital of France?");
        verify(memoryService).saveMessage("session-1", "assistant", "Paris is the capital of France.");
    }

    @Test
    void testToolExecutionFlow() {
        when(aiService.chat(anyString(), anyString()))
                .thenReturn("""
                        Thought: I need to check the weather.
                        Action: weather
                        Input: London
                        """)
                .thenReturn("""
                        Thought: I have the weather info.
                        Final Answer: The weather in London is sunny.
                        """);

        Tool weatherTool = mock(Tool.class);
        when(weatherTool.execute("London")).thenReturn("Sunny, 20°C");
        when(toolRegistry.getTool("weather")).thenReturn(weatherTool);
        when(toolRegistry.isToolEnabled("weather", null)).thenReturn(true);

        ReActAgent.AgentResult result = agent.run("What is the weather in London?", "session-1", null);

        assertTrue(result.answer().contains("The weather in London is sunny"));
        assertEquals(List.of("weather"), result.toolsUsed());
    }

    @Test
    void testUnknownTool() {
        when(aiService.chat(anyString(), anyString()))
                .thenReturn("""
                        Thought: I need to use a tool.
                        Action: nonexistent
                        Input: test
                        """)
                .thenReturn("""
                        Thought: That tool doesn't exist.
                        Final Answer: Cannot use that tool.
                        """);

        when(toolRegistry.getTool("nonexistent")).thenReturn(null);
        when(toolRegistry.getToolNames(null)).thenReturn("weather, calculator");

        ReActAgent.AgentResult result = agent.run("test", "session-1", null);

        assertTrue(result.answer().contains("Cannot use that tool"));
        assertTrue(result.toolsUsed().isEmpty());
    }

    @Test
    void testDisabledTool() {
        when(aiService.chat(anyString(), anyString()))
                .thenReturn("""
                        Thought: I need to use weather.
                        Action: weather
                        Input: London
                        """)
                .thenReturn("""
                        Thought: Tool is disabled.
                        Final Answer: Cannot use disabled tool.
                        """);

        Tool weatherTool = mock(Tool.class);
        when(toolRegistry.getTool("weather")).thenReturn(weatherTool);
        when(toolRegistry.isToolEnabled("weather", Set.of("calculator"))).thenReturn(false);
        when(toolRegistry.getToolNames(Set.of("calculator"))).thenReturn("calculator");

        ReActAgent.AgentResult result = agent.run("test", "session-1", Set.of("calculator"));

        assertTrue(result.answer().contains("Cannot use disabled tool"));
        assertTrue(result.toolsUsed().isEmpty());
    }

    @Test
    void testMaxIterationsFallback() {
        agentConfig.setMaxIterations(2);

        when(aiService.chat(anyString(), anyString())).thenReturn("""
                Thought: I'm thinking but not sure.
                """);

        ReActAgent.AgentResult result = agent.run("question", "session-1", null);

        assertNotNull(result.answer());
        assertTrue(result.toolsUsed().isEmpty());
        verify(memoryService, times(1)).saveMessage(eq("session-1"), eq("assistant"), anyString());
    }

    @Test
    void testThoughtExtractedWithAction() {
        when(aiService.chat(anyString(), anyString()))
                .thenReturn("""
                        Thought: Let me search for this.
                        Action: database
                        Input: spring boot
                        """)
                .thenReturn("""
                        Thought: I found the info.
                        Final Answer: Spring Boot is a framework.
                        """);

        Tool dbTool = mock(Tool.class);
        when(dbTool.execute("spring boot")).thenReturn("Found: Spring Boot info");
        when(toolRegistry.getTool("database")).thenReturn(dbTool);
        when(toolRegistry.isToolEnabled("database", null)).thenReturn(true);

        agent.run("What is Spring Boot?", "session-1", null);

        verify(kafkaPublisher).publishAgentEvent(eq("session-1"), eq("tool_call"), anyString());
    }

    @Test
    void testMultipleToolsUsed() {
        when(aiService.chat(anyString(), anyString()))
                .thenReturn("""
                        Thought: First check weather.
                        Action: weather
                        Input: London
                        """)
                .thenReturn("""
                        Thought: Now check news.
                        Action: news
                        Input: technology
                        """)
                .thenReturn("""
                        Thought: I have all info.
                        Final Answer: Here is the summary.
                        """);

        Tool weatherTool = mock(Tool.class);
        when(weatherTool.execute("London")).thenReturn("Sunny");
        Tool newsTool = mock(Tool.class);
        when(newsTool.execute("technology")).thenReturn("News results");
        when(toolRegistry.getTool("weather")).thenReturn(weatherTool);
        when(toolRegistry.getTool("news")).thenReturn(newsTool);
        when(toolRegistry.isToolEnabled("weather", null)).thenReturn(true);
        when(toolRegistry.isToolEnabled("news", null)).thenReturn(true);

        ReActAgent.AgentResult result = agent.run("Get weather and news", "session-1", null);

        assertEquals(List.of("weather", "news"), result.toolsUsed());
    }

    @Test
    void testEmptyLlmResponse() {
        when(aiService.chat(anyString(), anyString())).thenReturn("");

        ReActAgent.AgentResult result = agent.run("hello", "session-1", null);

        assertNotNull(result.answer());
    }
}
