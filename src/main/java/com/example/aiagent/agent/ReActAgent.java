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
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

        String ragContext = searchDocuments(userMessage);

        if (!ragContext.contains("No relevant documents found")) {
            return answerWithRag(userMessage, sessionId, ragContext);
        }

        return answerWithTools(userMessage, sessionId, enabledTools);
    }

    private AgentResult answerWithRag(String userMessage, String sessionId, String ragContext) {
        log.info("Using RAG Q&A for session {} (no ReAct loop)", sessionId);
        String systemPrompt = """
                You are a helpful document Q&A assistant. Answer the user's question based ONLY on the
                provided document context. If the context does not contain enough information, say so.
                Be specific and precise. Use exact numbers from the document when possible.
                """;
        String prompt = ragContext + "\n\nQuestion: " + userMessage;
        String llmResponse = aiService.chat(systemPrompt, prompt);
        String answer = llmResponse != null ? llmResponse.trim() : "";

        if (answer.isBlank() || answer.equals("No response")) {
            log.warn("RAG Q&A returned empty answer for session {}, raw response: {}", sessionId, llmResponse);
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

            log.info("LLM response (iteration {}): {}", state.getCurrentIteration(), llmResponse);

            Matcher finishMatcher = FINISH_PATTERN.matcher(llmResponse);
            if (finishMatcher.find()) {
                String finalAnswer = finishMatcher.group(1).trim();
                state.addThought("Final answer reached");
                state.setCompleted(true);
                memoryService.saveMessage(sessionId, "assistant", finalAnswer);
                kafkaPublisher.publishAgentEvent(sessionId, "final_answer", finalAnswer);
                return new AgentResult(finalAnswer, toolsUsed);
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
                    observation = tool.execute(toolInput);
                    toolsUsed.add(toolName);
                } else if (tool == null) {
                    observation = "Unknown tool: " + toolName + ". Available tools: " + toolRegistry.getToolNames(enabledTools);
                } else {
                    observation = "Tool '" + toolName + "' is not enabled. Available tools: " + toolRegistry.getToolNames(enabledTools);
                }
                state.addObservation(observation);
                kafkaPublisher.publishAgentEvent(sessionId, "tool_call",
                        toolName + " -> " + observation.substring(0, Math.min(100, observation.length())));
            } else {
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

    private String buildSystemPrompt(Set<String> enabledTools) {
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
                - Use one tool at a time. Do NOT simulate tool results.
                - Wait for the observation before continuing
                - Give a Final Answer when you have enough information
                - Be concise and helpful
                - Think step by step about which tool to use and what information you already have.
                """, toolRegistry.getToolDescriptions(enabledTools));
    }

    private String searchDocuments(String query) {
        try {
            var results = ingestionService.search(query, 5);
            if (results.isEmpty()) {
                return "No relevant documents found. Answer based on your general knowledge.";
            }
            StringBuilder sb = new StringBuilder("=== RELEVANT INFORMATION FROM UPLOADED DOCUMENTS ===\n");
            for (int i = 0; i < results.size(); i++) {
                Document doc = results.get(i);
                String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown");
                sb.append("--- Section ").append(i + 1).append(" (from: ").append(filename).append(") ---\n");
                sb.append(doc.getText()).append("\n\n");
            }
            sb.append("=== END OF DOCUMENT CONTEXT ===\n");
            return sb.toString();
        } catch (Exception e) {
            log.warn("Auto RAG search failed: {}", e.getMessage());
            return "No relevant documents found. Answer based on your general knowledge.";
        }
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

        prompt.append("What should you do next? Respond with exactly ONE Thought and either ONE Action and Input, or a Final Answer. Do not simulate tool results.");

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
