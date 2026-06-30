package com.example.aiagent.model;

import java.util.ArrayList;
import java.util.List;

public class AgentState {

    private String sessionId;
    private String userMessage;
    private List<String> thoughtHistory;
    private List<String> actionsTaken;
    private List<String> observations;
    private int currentIteration;
    private boolean completed;

    public AgentState() {
        this.thoughtHistory = new ArrayList<>();
        this.actionsTaken = new ArrayList<>();
        this.observations = new ArrayList<>();
        this.currentIteration = 0;
        this.completed = false;
    }

    public AgentState(String sessionId) {
        this();
        this.sessionId = sessionId;
    }

    public void addThought(String thought) {
        this.thoughtHistory.add(thought);
    }

    public void addAction(String action) {
        this.actionsTaken.add(action);
    }

    public void addObservation(String observation) {
        this.observations.add(observation);
    }

    public void incrementIteration() {
        this.currentIteration++;
    }

    public String getFormattedHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < thoughtHistory.size(); i++) {
            sb.append("Thought ").append(i + 1).append(": ").append(thoughtHistory.get(i)).append("\n");
            if (i < actionsTaken.size()) {
                sb.append("Action ").append(i + 1).append(": ").append(actionsTaken.get(i)).append("\n");
            }
            if (i < observations.size()) {
                sb.append("Observation ").append(i + 1).append(": ").append(observations.get(i)).append("\n");
            }
        }
        return sb.toString();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public List<String> getThoughtHistory() {
        return thoughtHistory;
    }

    public void setThoughtHistory(List<String> thoughtHistory) {
        this.thoughtHistory = thoughtHistory;
    }

    public List<String> getActionsTaken() {
        return actionsTaken;
    }

    public void setActionsTaken(List<String> actionsTaken) {
        this.actionsTaken = actionsTaken;
    }

    public List<String> getObservations() {
        return observations;
    }

    public void setObservations(List<String> observations) {
        this.observations = observations;
    }

    public int getCurrentIteration() {
        return currentIteration;
    }

    public void setCurrentIteration(int currentIteration) {
        this.currentIteration = currentIteration;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
