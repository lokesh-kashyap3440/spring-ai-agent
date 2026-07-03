package com.example.aiagent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AgentState {

    public record Step(String thought, String action, String observation) {}

    private String sessionId;
    private String userMessage;
    private List<Step> steps;
    private int currentIteration;
    private boolean completed;

    public AgentState() {
        this.steps = new ArrayList<>();
        this.currentIteration = 0;
        this.completed = false;
    }

    public AgentState(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    /**
     * Adds a new thought step. Creates a new {@link Step} with just the thought
     * and no action or observation yet.
     */
    public void addThought(String thought) {
        this.steps.add(new Step(thought, null, null));
    }

    /**
     * Sets the action on the most recent thought step. If no steps exist yet,
     * creates a new step with an empty thought.
     */
    public void addAction(String action) {
        if (steps.isEmpty()) {
            steps.add(new Step("", action, null));
        } else {
            int last = steps.size() - 1;
            Step prev = steps.get(last);
            steps.set(last, new Step(prev.thought(), action, prev.observation()));
        }
    }

    /**
     * Sets the observation on the most recent thought step. If no steps exist yet,
     * creates a new step with empty thought and action.
     */
    public void addObservation(String observation) {
        if (steps.isEmpty()) {
            steps.add(new Step("", null, observation));
        } else {
            int last = steps.size() - 1;
            Step prev = steps.get(last);
            steps.set(last, new Step(prev.thought(), prev.action(), observation));
        }
    }

    public void incrementIteration() {
        this.currentIteration++;
    }

    /**
     * Returns a formatted string of all steps, suitable for inclusion in prompts.
     */
    public String getFormattedHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            sb.append("Thought ").append(i + 1).append(": ").append(step.thought()).append("\n");
            if (step.action() != null) {
                sb.append("Action ").append(i + 1).append(": ").append(step.action()).append("\n");
            }
            if (step.observation() != null) {
                sb.append("Observation ").append(i + 1).append(": ").append(step.observation()).append("\n");
            }
        }
        return sb.toString();
    }

    // --- Primary accessors ---

    @JsonProperty("sessionId")
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @JsonProperty("userMessage")
    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    @JsonProperty("steps")
    public List<Step> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    @JsonProperty("currentIteration")
    public int getCurrentIteration() {
        return currentIteration;
    }

    public void setCurrentIteration(int currentIteration) {
        this.currentIteration = currentIteration;
    }

    @JsonProperty("completed")
    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    // --- Backward-compatible derived accessors (not serialized) ---

    @JsonIgnore
    public List<String> getThoughtHistory() {
        return steps.stream()
                .map(Step::thought)
                .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<String> getActionsTaken() {
        return steps.stream()
                .map(Step::action)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @JsonIgnore
    public List<String> getObservations() {
        return steps.stream()
                .map(Step::observation)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // --- Setters that accept lists (for backward compatibility / deserialization) ---

    /**
     * Estimates the token count of all steps' thought/action/observation text.
     * Uses a rough heuristic: total characters / 4.
     *
     * @return estimated token count (rough approximation)
     */
    @JsonIgnore
    public long estimatedTokenCount() {
        long totalChars = 0;
        for (Step step : steps) {
            if (step.thought() != null) {
                totalChars += step.thought().length();
            }
            if (step.action() != null) {
                totalChars += step.action().length();
            }
            if (step.observation() != null) {
                totalChars += step.observation().length();
            }
        }
        return totalChars / 4;
    }

    /**
     * Removes the oldest steps until the estimated token count is within the
     * specified limit. At least one step is always retained.
     *
     * @param maxTokens the maximum estimated token count to allow
     * @return the number of steps removed
     */
    @JsonIgnore
    public int truncateStepsToFit(long maxTokens) {
        int removed = 0;
        while (steps.size() > 1 && estimatedTokenCount() > maxTokens) {
            steps.removeFirst();
            removed++;
        }
        return removed;
    }

    public void setThoughtHistory(List<String> thoughtHistory) {
        // This setter exists for backward compatibility; when deserializing from
        // the old list format, rebuild steps from the thought list.
        rebuildStepsFromLists(thoughtHistory, getActionsTaken(), getObservations());
    }

    public void setActionsTaken(List<String> actionsTaken) {
        rebuildStepsFromLists(getThoughtHistory(), actionsTaken, getObservations());
    }

    public void setObservations(List<String> observations) {
        rebuildStepsFromLists(getThoughtHistory(), getActionsTaken(), observations);
    }

    private void rebuildStepsFromLists(List<String> thoughts, List<String> actions, List<String> observations) {
        this.steps.clear();
        int max = Math.max(thoughts.size(), Math.max(actions.size(), observations.size()));
        for (int i = 0; i < max; i++) {
            String thought = i < thoughts.size() ? thoughts.get(i) : "";
            String action = i < actions.size() ? actions.get(i) : null;
            String obs = i < observations.size() ? observations.get(i) : null;
            this.steps.add(new Step(thought, action, obs));
        }
    }
}
