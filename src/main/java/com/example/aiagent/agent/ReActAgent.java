package com.example.aiagent.agent;

import com.example.aiagent.config.AgentConfig;
import com.example.aiagent.memory.AgentMemoryService;
import com.example.aiagent.model.AgentState;
import com.example.aiagent.service.KafkaEventPublisher;
import com.example.aiagent.service.OllamaService;
import com.example.aiagent.tools.Tool;
import com.example.aiagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final Pattern ACTION_PATTERN = Pattern.compile(
        "Action:\\s*(\\w+)\\s*\\n?\\s*Input:\\s*(.+?)(?=\\n|$)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FINISH_PATTERN = Pattern.compile(
        "Final Answer:\\s*(.+)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private final OllamaService ollamaService;
    private final AgentMemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final AgentConfig agentConfig;
    private final KafkaEventPublisher kafkaPublisher;

    public ReActAgent(OllamaService ollamaService, AgentMemoryService memoryService,
                      ToolRegistry toolRegistry, AgentConfig agentConfig,
                      KafkaEventPublisher kafkaPublisher) {
        this.ollamaService = ollamaService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.agentConfig = agentConfig;
        this.kafkaPublisher = kafkaPublisher;
    }

    public String run(String userMessage, String sessionId) {
        AgentState state = new AgentState(sessionId);
        state.setUserMessage(userMessage);

        memoryService.saveMessage(sessionId, "user", userMessage);
        kafkaPublisher.publishAgentEvent(sessionId, "user_message", userMessage);

        String systemPrompt = buildSystemPrompt();
        String context = buildContext(sessionId, userMessage);

        for (int i = 0; i < agentConfig.getMaxIterations(); i++) {
            state.incrementIteration();
            log.info("Agent iteration {} for session {}", state.getCurrentIteration(), sessionId);

            String prompt = buildIterationPrompt(state, context);
            String llmResponse = ollamaService.chat(systemPrompt, prompt);

            log.debug("LLM response: {}", llmResponse);

            Matcher finishMatcher = FINISH_PATTERN.matcher(llmResponse);
            if (finishMatcher.find()) {
                String finalAnswer = finishMatcher.group(1).trim();
                state.addThought("Final answer reached");
                state.setCompleted(true);

                memoryService.saveMessage(sessionId, "assistant", finalAnswer);
                kafkaPublisher.publishAgentEvent(sessionId, "final_answer", finalAnswer);

                return finalAnswer;
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(llmResponse);
            if (actionMatcher.find()) {
                String toolName = actionMatcher.group(1).trim();
                String toolInput = actionMatcher.group(2).trim();

                state.addThought(extractThought(llmResponse));
                state.addAction(toolName + "(" + toolInput + ")");

                Tool tool = toolRegistry.getTool(toolName);
                String observation;
                if (tool != null) {
                    observation = tool.execute(toolInput);
                } else {
                    observation = "Unknown tool: " + toolName + ". Available tools: " + toolRegistry.getToolNames();
                }
                state.addObservation(observation);

                kafkaPublisher.publishAgentEvent(sessionId, "tool_call",
                    toolName + " -> " + observation.substring(0, Math.min(100, observation.length())));
            } else {
                state.addThought(llmResponse);
                if (i == agentConfig.getMaxIterations() - 1) {
                    String fallbackAnswer = extractAnswer(llmResponse);
                    memoryService.saveMessage(sessionId, "assistant", fallbackAnswer);
                    return fallbackAnswer;
                }
            }
        }

        String lastThought = state.getThoughtHistory().isEmpty() ?
            "I could not determine a final answer." :
            state.getThoughtHistory().get(state.getThoughtHistory().size() - 1);
        memoryService.saveMessage(sessionId, "assistant", lastThought);
        return lastThought;
    }

    private String buildSystemPrompt() {
        return String.format("""
            You are a helpful AI agent that uses the ReAct (Reasoning + Acting) pattern.

            You have access to the following tools:
            %s

            To use a tool, respond in this exact format:
            Thought: [your reasoning about what to do]
            Action: [tool_name]
            Input: [the input for the tool]

            When you have enough information to answer, use:
            Thought: [your final reasoning]
            Final Answer: [your answer to the user]

            Rules:
            - Always start with a Thought
            - Use one tool at a time
            - Wait for the observation before continuing
            - Give a Final Answer when you have enough information
            - Be concise and helpful
            """, toolRegistry.getToolDescriptions());
    }

    private String buildContext(String sessionId, String userMessage) {
        String history = memoryService.getFormattedHistory(sessionId);
        return String.format("Conversation History:\n%s\n\nUser's current message: %s", history, userMessage);
    }

    private String buildIterationPrompt(AgentState state, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(context).append("\n\n");

        if (!state.getThoughtHistory().isEmpty()) {
            prompt.append("Previous steps:\n");
            prompt.append(state.getFormattedHistory());
            prompt.append("\n");
        }

        prompt.append("What should you do next? Respond with a Thought and either an Action or Final Answer.");

        return prompt.toString();
    }

    private String extractThought(String response) {
        Pattern thoughtPattern = Pattern.compile("Thought:\\s*(.+?)(?=\\nAction:|$)", Pattern.DOTALL);
        Matcher matcher = thoughtPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response.substring(0, Math.min(200, response.length()));
    }

    private String extractAnswer(String response) {
        Pattern answerPattern = Pattern.compile("Final Answer:\\s*(.+)", Pattern.DOTALL);
        Matcher matcher = answerPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response.substring(0, Math.min(500, response.length()));
    }
}
