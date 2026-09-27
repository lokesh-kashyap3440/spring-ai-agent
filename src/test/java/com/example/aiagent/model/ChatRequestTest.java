package com.example.aiagent.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void testDefaultConstructor() {
        ChatRequest request = new ChatRequest();
        assertNull(request.getMessage());
        assertNull(request.getSessionId());
        assertNull(request.getToolsEnabled());
    }

    @Test
    void testParameterizedConstructor() {
        ChatRequest request = new ChatRequest("hello", "session-1");
        assertEquals("hello", request.getMessage());
        assertEquals("session-1", request.getSessionId());
        assertNull(request.getToolsEnabled());
    }

    @Test
    void testSettersAndGetters() {
        ChatRequest request = new ChatRequest();
        request.setMessage("test message");
        request.setSessionId("test-session");
        request.setToolsEnabled(List.of("weather", "news"));

        assertEquals("test message", request.getMessage());
        assertEquals("test-session", request.getSessionId());
        assertEquals(List.of("weather", "news"), request.getToolsEnabled());
    }

    @Test
    void testValidationFailsForBlankMessage() {
        ChatRequest request = new ChatRequest("", null);
        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testValidationPassesForValidMessage() {
        ChatRequest request = new ChatRequest("valid message", null);
        var violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testToolsEnabledCanBeNull() {
        ChatRequest request = new ChatRequest("test", "session-1");
        request.setToolsEnabled(null);
        assertNull(request.getToolsEnabled());
    }
}
