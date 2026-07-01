package com.example.aiagent.agent;

import com.example.aiagent.config.AgentConfig;
import com.example.aiagent.memory.AgentMemoryService;
import com.example.aiagent.model.AgentState;
import com.example.aiagent.service.DocumentIngestionService;
import com.example.aiagent.service.KafkaEventPublisher;
import com.example.aiagent.service.AiService;
import com.example.aiagent.tools.Tool;
import com.example.aiagent.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct (Reasoning + Acting) agent that iteratively reasons about user queries
 * and invokes tools to gather information before producing a final answer.
 *
 * <p>The agent follows this loop for each iteration:</p>
 * <ol>
 *   <li>Sends the conversation context to the LLM</li>
 *   <li>Parses the LLM response for either an Action (tool call) or Final Answer</li>
 *   <li>If Action: executes the tool and feeds the observation back into the next iteration</li>
 *   <li>If Final Answer: returns the answer to the caller</li>
 * </ol>
 *
 * <p>Special behaviors:</p>
 * <ul>
 *   <li>RAG auto-retry: if the LLM gives a final answer on the first iteration without
 *       using any tools and RAG search is available, the agent force-calls rag_search</li>
 *   <li>RAG delegation: when rag_search is called, the agent bypasses the normal loop
 *       and performs a direct Q&A with the retrieved context</li>
 *   <li>Tool error handling: if a tool throws an exception, the error is returned as
 *       an observation rather than crashing the agent loop</li>
 * </ul>
 */
@Component
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "Action:\\s*(\\w+)\\s*[;|\\n]?\\s*Input:\\s*(.+?)(?=\\n|$)", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FINISH_PATTERN = Pattern.compile(
            "Final Answer:\\s*(.+)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    private final AiService aiService;
    private final AgentMemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final AgentConfig agentConfig;
    private final KafkaEventPublisher kafkaPublisher;
    private final DocumentIngestionService ingestionService;

    public ReActAgent(AiService aiService, AgentMemoryService memoryService,
                      ToolRegistry toolRegistry, AgentConfig agentConfig,
                      KafkaEventPublisher kafkaPublisher,
                      DocumentIngestionService ingestionService) {
        this.aiService = aiService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.agentConfig = agentConfig;
        this.kafkaPublisher = kafkaPublisher;
        this.ingestionService = ingestionService;
    }

    public record AgentResult(String answer, List<String> toolsUsed) {}

    public AgentResult run(String userMessage, String sessionId, Set<String> enabledTools) {
        memoryService.saveMessage(sessionId, "user", userMessage);
        kafkaPublisher.publishAgentEvent(sessionId, "user_message", userMessage);

        return answerWithTools(userMessage, sessionId, enabledTools);
    }

    private AgentResult answerWithRag(String userMessage, String sessionId, String ragContext) {
        log.info("Using RAG Q&A for session {} (no ReAct loop)", sessionId);

        String prompt = """
                Here is the document data:
                """ + ragContext + "\n\nQuestion: " + userMessage;
        String llmResponse = aiService.chat("", prompt);
        log.info("RAG Q&A raw response: [{}]", llmResponse);
        String answer = llmResponse != null ? llmResponse.trim() : "";

        if (answer.isBlank() || answer.equals("No response")) {
            log.warn("RAG Q&A returned empty answer for session {}, raw response: [{}]", sessionId, llmResponse);
            answer = "Based on the uploaded documents, I couldn't find enough information to answer that question. Try rephrasing or ask about a different topic.";
        }

        memoryService.saveMessage(sessionId, "assistant", answer);
        return new AgentResult(answer, List.of("rag_search"));
    }

    private AgentResult answerWithTools(String userMessage, String sessionId, Set<String> enabledTools) {
        AgentState state = new AgentState(sessionId);
        state.setUserMessage(userMessage);
        List<String> toolsUsed = new ArrayList<>();
        String systemPrompt = buildSystemPrompt(enabledTools);
        String context = buildContext(sessionId, userMessage);

        for (int i = 0; i < agentConfig.getMaxIterations(); i++) {
            state.incrementIteration();
            log.info("Agent iteration {} for session {}", state.getCurrentIteration(), sessionId);

            String prompt = buildIterationPrompt(state, context);
            String llmResponse = aiService.chat(systemPrompt, prompt);

            log.debug("LLM response (iteration {}): {}", state.getCurrentIteration(), llmResponse);

            if (llmResponse == null || llmResponse.isBlank() || llmResponse.equals("No response")) {
                log.warn("Empty LLM response at iteration {}, stopping", state.getCurrentIteration());
                String fallback = "I could not determine the answer. Please try rephrasing your question.";
                memoryService.saveMessage(sessionId, "assistant", fallback);
                return new AgentResult(fallback, toolsUsed);
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(llmResponse);
            if (actionMatcher.find()) {
                String toolName = actionMatcher.group(1).trim();
                String toolInput = actionMatcher.group(2).trim();

                state.addThought(extractThought(llmResponse));
                state.addAction(toolName + "(" + toolInput + ")");

                Tool tool = toolRegistry.getTool(toolName);
                String observation;
                if (tool != null && toolRegistry.isToolEnabled(toolName, enabledTools)) {
                    try {
                        observation = tool.execute(toolInput);
                    } catch (Exception e) {
                        log.error("Tool '{}' threw exception: {}", toolName, e.getMessage(), e);
                        observation = "Error executing tool '" + toolName + "': " + e.getMessage();
                    }
                    toolsUsed.add(toolName);
                } else if (tool == null) {
                    observation = "Unknown tool: " + toolName + ". Available tools: " + toolRegistry.getToolNames(enabledTools);
                } else {
                    observation = "Tool '" + toolName + "' is not enabled. Available tools: " + toolRegistry.getToolNames(enabledTools);
                }

                if ("rag_search".equals(toolName)) {
                    return answerWithRag(userMessage, sessionId, observation);
                }

                String truncatedObs = observation.length() > 2000 ? observation.substring(0, 2000) + "... [truncated]" : observation;
                state.addObservation(truncatedObs);
                kafkaPublisher.publishAgentEvent(sessionId, "tool_call",
                        toolName + " -> " + observation.substring(0, Math.min(100, observation.length())));
            } else {
                Matcher finishMatcher = FINISH_PATTERN.matcher(llmResponse);
                if (finishMatcher.find()) {
                    String finalAnswer = finishMatcher.group(1).trim();
                    // If no tools used and rag_search is available, force a retry
                    if (toolsUsed.isEmpty() && i == 0 && isRagSearchEnabled(enabledTools)) {
                        state.addThought("Auto-calling rag_search after premature final answer");
                        String searchQuery = userMessage.replaceAll("(?i)based on the (uploaded )?document( [a-zA-Z0-9_.-]+)?[,\\s]*", "").trim();
                        String observation = toolRegistry.getTool("rag_search").execute(searchQuery);
                        toolsUsed.add("rag_search");
                        return answerWithRag(userMessage, sessionId, observation);
                    }
                    state.addThought("Final answer reached");
                    state.setCompleted(true);
                    memoryService.saveMessage(sessionId, "assistant", finalAnswer);
                    kafkaPublisher.publishAgentEvent(sessionId, "final_answer", finalAnswer);
                    return new AgentResult(finalAnswer, toolsUsed);
                }
                state.addThought(llmResponse);
                if (i == agentConfig.getMaxIterations() - 1) {
                    String fallbackAnswer = extractAnswer(llmResponse);
                    memoryService.saveMessage(sessionId, "assistant", fallbackAnswer);
                    return new AgentResult(fallbackAnswer, toolsUsed);
                }
            }
        }

        String lastThought = state.getThoughtHistory().isEmpty() ?
                "I could not determine a final answer." :
                state.getThoughtHistory().get(state.getThoughtHistory().size() - 1);
        memoryService.saveMessage(sessionId, "assistant", lastThought);
        return new AgentResult(lastThought, toolsUsed);
    }

    private boolean isRagSearchEnabled(Set<String> enabledTools) {
        return enabledTools == null || enabledTools.contains("rag_search");
    }

    private String buildSystemPrompt(Set<String> enabledTools) {
        return String.format("""
                You are a helpful AI agent that uses the ReAct (Reasoning + Acting) pattern.
                
                You have access to the following tools:
                %s
                
                TOOL SELECTION GUIDE:
                - Use "rag_search" ONLY when the user asks about content from uploaded documents (files).
                - Use "database" ONLY for general technology definitions (Spring Boot, Kafka, etc.).
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
                """, toolRegistry.getToolDescriptions(enabledTools));
    }



    private String buildContext(String sessionId, String userMessage) {
        String history = memoryService.getFormattedHistory(sessionId);
        return String.format("Conversation History:\n%s\n\nUser's current message: %s", history, userMessage);
    }

    private String buildIterationPrompt(AgentState state, String context) {
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

    private String extractThought(String response) {
        Pattern thoughtPattern = Pattern.compile("Thought:\\s*(.+?)(?=\\nAction:|$)", Pattern.DOTALL);
        Matcher matcher = thoughtPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return response.substring(0, Math.min(200, response.length()));
    }

    private String extractAnswer(String response) {
        if (response == null || response.isBlank() || response.equals("No response")) {
            return "I could not determine a complete answer. Please rephrase your question or check if the document contains the relevant information.";
        }
        Pattern answerPattern = Pattern.compile("Final Answer:\\s*(.+)", Pattern.DOTALL);
        Matcher matcher = answerPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        String cleaned = response.replaceAll("^Thought:\\s*", "").replaceAll("\\s*Action:\\s*\\w+\\s*\\n?\\s*Input:\\s*.*$", "").trim();
        if (cleaned.length() > 20) {
            return cleaned.substring(0, Math.min(500, cleaned.length()));
        }
        return response.substring(0, Math.min(500, response.length()));
    }
}
