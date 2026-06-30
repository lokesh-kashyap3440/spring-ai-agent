package com.example.aiagent.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentStateTest {

    @Test
    void testDefaultConstructor() {
        AgentState state = new AgentState();
        assertNull(state.getSessionId());
        assertTrue(state.getThoughtHistory().isEmpty());
        assertTrue(state.getActionsTaken().isEmpty());
        assertTrue(state.getObservations().isEmpty());
        assertEquals(0, state.getCurrentIteration());
        assertFalse(state.isCompleted());
    }

    @Test
    void testConstructorWithSessionId() {
        AgentState state = new AgentState("session-1");
        assertEquals("session-1", state.getSessionId());
        assertTrue(state.getThoughtHistory().isEmpty());
    }

    @Test
    void testAddThought() {
        AgentState state = new AgentState();
        state.addThought("thought 1");
        state.addThought("thought 2");
        assertEquals(2, state.getThoughtHistory().size());
        assertEquals("thought 1", state.getThoughtHistory().get(0));
        assertEquals("thought 2", state.getThoughtHistory().get(1));
    }

    @Test
    void testAddAction() {
        AgentState state = new AgentState();
        state.addAction("action 1");
        assertEquals(1, state.getActionsTaken().size());
        assertEquals("action 1", state.getActionsTaken().get(0));
    }

    @Test
    void testAddObservation() {
        AgentState state = new AgentState();
        state.addObservation("observation 1");
        assertEquals(1, state.getObservations().size());
        assertEquals("observation 1", state.getObservations().get(0));
    }

    @Test
    void testIncrementIteration() {
        AgentState state = new AgentState();
        assertEquals(0, state.getCurrentIteration());
        state.incrementIteration();
        assertEquals(1, state.getCurrentIteration());
        state.incrementIteration();
        assertEquals(2, state.getCurrentIteration());
    }

    @Test
    void testCompletedFlag() {
        AgentState state = new AgentState();
        assertFalse(state.isCompleted());
        state.setCompleted(true);
        assertTrue(state.isCompleted());
    }

    @Test
    void testGetFormattedHistoryEmpty() {
        AgentState state = new AgentState();
        assertEquals("", state.getFormattedHistory());
    }

    @Test
    void testGetFormattedHistoryWithThoughtOnly() {
        AgentState state = new AgentState();
        state.addThought("my thought");
        assertEquals("Thought 1: my thought\n", state.getFormattedHistory());
    }

    @Test
    void testGetFormattedHistoryFull() {
        AgentState state = new AgentState();
        state.addThought("first thought");
        state.addAction("tool1(input)");
        state.addObservation("result");

        String formatted = state.getFormattedHistory();
        assertTrue(formatted.contains("Thought 1: first thought"));
        assertTrue(formatted.contains("Action 1: tool1(input)"));
        assertTrue(formatted.contains("Observation 1: result"));
    }

    @Test
    void testSetters() {
        AgentState state = new AgentState();
        state.setSessionId("sid");
        state.setUserMessage("hi");
        state.setCurrentIteration(5);

        assertEquals("sid", state.getSessionId());
        assertEquals("hi", state.getUserMessage());
        assertEquals(5, state.getCurrentIteration());
    }
}
