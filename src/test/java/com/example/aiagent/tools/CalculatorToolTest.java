package com.example.aiagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorToolTest {

    private CalculatorTool tool;

    @BeforeEach
    void setUp() {
        tool = new CalculatorTool();
    }

    @Test
    void testName() {
        assertEquals("calculator", tool.getName());
    }

    @Test
    void testDescription() {
        assertTrue(tool.getDescription().contains("Evaluate mathematical expressions"));
    }

    @Test
    void testAddition() {
        String result = tool.execute("2 + 3");
        assertEquals("Result: 5", result);
    }

    @Test
    void testSubtraction() {
        String result = tool.execute("10 - 4");
        assertEquals("Result: 6", result);
    }

    @Test
    void testMultiplication() {
        String result = tool.execute("6 * 7");
        assertEquals("Result: 42", result);
    }

    @Test
    void testDivision() {
        String result = tool.execute("20 / 4");
        assertEquals("Result: 5", result);
    }

    @Test
    void testOrderOfOperations() {
        String result = tool.execute("2 + 3 * 4");
        assertEquals("Result: 14", result);
    }

    @Test
    void testSqrt() {
        String result = tool.execute("sqrt(16)");
        assertEquals("Result: 4", result);
    }

    @Test
    void testSqrtNonPerfectSquare() {
        String result = tool.execute("sqrt(2)");
        assertTrue(result.startsWith("Result:"));
        assertTrue(Double.parseDouble(result.replace("Result: ", "")) > 1.4);
    }

    @Test
    void testDecimalResult() {
        String result = tool.execute("5 / 2");
        assertEquals("Result: 2.500000", result);
    }

    @Test
    void testDivisionByZero() {
        String result = tool.execute("10 / 0");
        assertTrue(result.startsWith("Error"));
        assertTrue(result.contains("Division by zero"));
    }

    @Test
    void testInvalidExpression() {
        String result = tool.execute("abc");
        assertTrue(result.startsWith("Error"));
    }

    @Test
    void testWhitespaceHandling() {
        assertEquals("Result: 5", tool.execute("  2 + 3  "));
    }

    @Test
    void testComplexExpression() {
        String result = tool.execute("sqrt(9) + 1");
        assertEquals("Result: 4", result);
    }
}
