package com.example.aiagent.agent;

import com.example.aiagent.memory.AgentMemoryService;
import com.example.aiagent.model.AgentState;
import com.example.aiagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Builds prompts for the ReAct agent's interactions with the LLM.
 *
 * <p>This class encapsulates the prompt construction logic that was previously
 * inlined in {@link ReActAgent}, keeping prompt concerns separate from
 * agent orchestration.</p>
 */
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);
    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/system-prompt.txt";

    private final ToolRegistry toolRegistry;
    private final AgentMemoryService memoryService;
    private final String systemPromptTemplate;

    public PromptBuilder(ToolRegistry toolRegistry, AgentMemoryService memoryService) {
        this.toolRegistry = toolRegistry;
        this.memoryService = memoryService;
        this.systemPromptTemplate = loadSystemPromptTemplate();
    }

    /**
     * Loads the system prompt template from the classpath resource
     * {@value #SYSTEM_PROMPT_RESOURCE}. Falls back to a hardcoded template
     * if the resource cannot be read.
     */
    private static String loadSystemPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(SYSTEM_PROMPT_RESOURCE);
            if (resource.exists()) {
                byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Could not load system prompt template from '{}', using fallback", SYSTEM_PROMPT_RESOURCE, e);
        }
        return """
                You are a helpful AI agent that uses the ReAct (Reasoning + Acting) pattern.

                You have access to the following tools:
                {tool_descriptions}

                TOOL SELECTION GUIDE:
                - Use "rag_search" ONLY when the user asks about content from uploaded documents (files).
                - Use "knowledge_base" ONLY for general technology definitions (Spring Boot, Kafka, etc.).
                - Use "calculate" for math expressions.
                - Use "weather" for current weather.
                - Use "news" for current news.

                CRITICAL OUTPUT RULES:
                - You must output EXACTLY ONE of these two formats per response.
                  FORMAT 1 (use a tool): Thought: ... | Action: tool_name | Input: tool_input
                  FORMAT 2 (final answer): Thought: ... | Final Answer: ...
                - NEVER output both Action and Final Answer in the same response.
                - NEVER include Final Answer when you write Action. Only Action+Input.
                - NEVER simulate tool results. Only output the Action. Wait for the observation.
                - Always start with a Thought.

                MANDATORY: If the user's question references uploaded documents, files, or a PDF,
                you MUST call rag_search first. Do NOT skip this step. Even if you think you know
                the answer, you must search the documents to provide accurate information.
                """;
    }

    /**
     * Builds the system prompt describing the agent's role, available tools,
     * and output formatting rules.
     */
    public String buildSystemPrompt(Set<String> enabledTools) {
        String toolDescriptions = toolRegistry.getToolDescriptions(enabledTools);
        return systemPromptTemplate.replace("{tool_descriptions}", toolDescriptions);
    }

    /**
     * Builds the context prompt incorporating conversation history and the
     * user's current message.
     */
    public String buildContext(String sessionId, String userMessage) {
        String history = memoryService.getFormattedHistory(sessionId);
        return String.format("Conversation History:\n%s\n\nUser's current message: %s", history, userMessage);
    }

    /**
     * Builds the iteration prompt that includes the conversation context and
     * the previous reasoning/action/observation steps, asking the LLM what to
     * do next.
     */
    public String buildIterationPrompt(AgentState state, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(context).append("\n\n");

        if (!state.getThoughtHistory().isEmpty()) {
            prompt.append("Previous steps (last 2):\n");
            String history = state.getFormattedHistory();
            String[] lines = history.split("\n");
            int start = Math.max(0, lines.length - 15);
            for (int j = start; j < lines.length; j++) {
                prompt.append(lines[j]).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("What should you do next? Respond with exactly ONE Thought and either ONE Action+Input (to call a tool) OR a Final Answer (to give your answer). Never include both. Do not simulate tool results.");

        return prompt.toString();
    }
}
