package com.example.aiagent.agent;

import com.example.aiagent.config.AgentConfig;
import com.example.aiagent.memory.AgentMemoryService;
import com.example.aiagent.model.AgentState;
import com.example.aiagent.service.DocumentIngestionService;
import com.example.aiagent.service.KafkaEventPublisher;
import com.example.aiagent.service.AiService;
import com.example.aiagent.tools.Tool;
import com.example.aiagent.tools.ToolNames;
import com.example.aiagent.tools.ToolRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
 *       using any tools and documents exist in the ingestion service,
 *       the agent logs a warning and force-calls rag_search</li>
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
    private static final int MAX_OBSERVATION_LENGTH = 2000;

    private final AiService aiService;
    private final AgentMemoryService memoryService;
    private final ToolRegistry toolRegistry;
    private final AgentConfig agentConfig;
    private final KafkaEventPublisher kafkaPublisher;
    private final DocumentIngestionService ingestionService;
    private final PromptBuilder promptBuilder;
    private final MeterRegistry meterRegistry;
    private final Counter toolCallCounter;
    private final Timer agentLoopTimer;

    public ReActAgent(AiService aiService, AgentMemoryService memoryService,
                      ToolRegistry toolRegistry, AgentConfig agentConfig,
                      KafkaEventPublisher kafkaPublisher,
                      DocumentIngestionService ingestionService,
                      MeterRegistry meterRegistry) {
        this.aiService = aiService;
        this.memoryService = memoryService;
        this.toolRegistry = toolRegistry;
        this.agentConfig = agentConfig;
        this.kafkaPublisher = kafkaPublisher;
        this.ingestionService = ingestionService;
        this.meterRegistry = meterRegistry;
        this.promptBuilder = new PromptBuilder(toolRegistry, memoryService);
        this.toolCallCounter = Counter.builder("agent.tool.calls")
                .description("Total number of tool calls made by the agent")
                .register(meterRegistry);
        this.agentLoopTimer = Timer.builder("agent.loop.duration")
                .description("Duration of agent loop execution sessions")
                .register(meterRegistry);
    }

    public record AgentResult(String answer, List<String> toolsUsed) {}

    public AgentResult run(String userMessage, String sessionId, Set<String> enabledTools) {
        Timer.Sample sample = Timer.start(meterRegistry);

        memoryService.saveMessage(sessionId, "user", userMessage);
        kafkaPublisher.publishAgentEvent(sessionId, "user_message", userMessage);

        AgentResult result = answerWithTools(userMessage, sessionId, enabledTools);

        sample.stop(agentLoopTimer);
        return result;
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
        return new AgentResult(answer, List.of(ToolNames.RAG_SEARCH));
    }

    private AgentResult answerWithTools(String userMessage, String sessionId, Set<String> enabledTools) {
        AgentState state = new AgentState(sessionId);
        state.setUserMessage(userMessage);
        List<String> toolsUsed = new ArrayList<>();
        String systemPrompt = promptBuilder.buildSystemPrompt(enabledTools);
        String context = promptBuilder.buildContext(sessionId, userMessage);

        for (int i = 0; i < agentConfig.getMaxIterations(); i++) {
            state.incrementIteration();
            log.info("Agent iteration {} for session {}", state.getCurrentIteration(), sessionId);

            // Context window management: estimate token count and truncate if needed
            long estimatedTokens = state.estimatedTokenCount();
            int maxTokens = agentConfig.getMaxContextTokens();
            if (estimatedTokens > maxTokens) {
                int removedSteps = state.truncateStepsToFit(maxTokens);
                log.warn("Context window limit reached (estimated {} tokens, max {}). Truncated {} oldest step(s) for session {}.",
                        estimatedTokens, maxTokens, removedSteps, sessionId);
            }

            String prompt = promptBuilder.buildIterationPrompt(state, context);
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
                    toolCallCounter.increment();
                    Timer.Sample toolSample = Timer.start(meterRegistry);
                    try {
                        observation = tool.execute(toolInput);
                    } catch (Exception e) {
                        log.error("Tool '{}' threw exception: {}", toolName, e.getMessage(), e);
                        observation = "Error executing tool '" + toolName + "': " + e.getMessage();
                    } finally {
                        toolSample.stop(Timer.builder("agent.tool.duration")
                                .description("Execution duration per tool")
                                .tag("tool", toolName)
                                .register(meterRegistry));
                    }
                    toolsUsed.add(toolName);
                } else if (tool == null) {
                    observation = "Unknown tool: " + toolName + ". Available tools: " + toolRegistry.getToolNames(enabledTools);
                } else {
                    observation = "Tool '" + toolName + "' is not enabled. Available tools: " + toolRegistry.getToolNames(enabledTools);
                }

                if (ToolNames.RAG_SEARCH.equals(toolName)) {
                    return answerWithRag(userMessage, sessionId, observation);
                }

                String truncatedObs = truncateObservation(observation);
                state.addObservation(truncatedObs);
                kafkaPublisher.publishAgentEvent(sessionId, "tool_call",
                        toolName + " -> " + observation.substring(0, Math.min(100, observation.length())));
            } else {
                Matcher finishMatcher = FINISH_PATTERN.matcher(llmResponse);
                if (finishMatcher.find()) {
                    String finalAnswer = finishMatcher.group(1).trim();
                    // If no tools used, first iteration, RAG search is enabled, and documents exist, force a retry
                    if (toolsUsed.isEmpty() && i == 0 && isRagSearchEnabled(enabledTools)
                            && !ingestionService.listDocuments(null).isEmpty()) {
                        log.warn("Overriding LLM decision: forcing rag_search call after premature final answer on first iteration for session {}", sessionId);
                        state.addThought("Auto-calling rag_search after premature final answer");
                        String searchQuery = userMessage.replaceAll("(?i)based on the (uploaded )?document( [a-zA-Z0-9_.-]+)?[,\\s]*", "").trim();
                        String observation = toolRegistry.getTool(ToolNames.RAG_SEARCH).execute(searchQuery);
                        toolsUsed.add(ToolNames.RAG_SEARCH);
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
        return enabledTools == null || enabledTools.contains(ToolNames.RAG_SEARCH);
    }

    /**
     * Truncates an observation to {@link #MAX_OBSERVATION_LENGTH} characters,
     * appending a suffix indicating how many characters were omitted.
     */
    private static String truncateObservation(String observation) {
        if (observation.length() > MAX_OBSERVATION_LENGTH) {
            int omitted = observation.length() - MAX_OBSERVATION_LENGTH;
            return observation.substring(0, MAX_OBSERVATION_LENGTH)
                    + "... [truncated -- " + omitted + " chars omitted]";
        }
        return observation;
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
