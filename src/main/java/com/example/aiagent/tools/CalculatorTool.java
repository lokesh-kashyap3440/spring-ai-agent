package com.example.aiagent.tools;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * A tool that evaluates mathematical expressions using Spring Expression Language (SpEL).
 *
 * <p>Supports standard arithmetic: addition (+), subtraction (-), multiplication (*),
 * division (/), parentheses for grouping, and relational/conditional operators.
 * Operator precedence follows standard mathematical rules (e.g., {@code 2 + 3 * 4}
 * evaluates as {@code 14}).</p>
 */
@Component
public class CalculatorTool implements Tool {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "Evaluate mathematical expressions. Input: math expression (e.g., '2 + 2' or '2 * 3 + 4')";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "expression", Map.of("type", "string", "description", "Math expression (e.g., 2 + 2)")
            ),
            "required", List.of("expression")
        );
    }

    @Override
    public String execute(String input) {
        try {
            String expression = input.trim();

            // Pre-process sqrt() calls: sqrt(X) -> T(java.lang.Math).sqrt(X)
            expression = expression.replaceAll("sqrt\\s*\\(\\s*([^()]+)\\s*\\)", "T(java.lang.Math).sqrt($1)");

            // Sanitize: only allow digits, operators, parentheses, decimal points, whitespace, and letters for function names
            if (!expression.matches("[0-9+\\-*/().%\\sA-Za-z]+")) {
                return "Error: Expression contains invalid characters. Only numbers, operators (+, -, *, /), parentheses, and % are allowed.";
            }

            // Force floating-point evaluation to handle non-integer division correctly.
            // No parentheses: 1.0 * 5 / 2 -> (1.0 * 5) / 2 = 2.5 (left-to-right evaluation).
            // With parentheses, 1.0 * (5 / 2) would still do integer division inside the parens.
            String floatExpr = "1.0 * " + expression;
            double result = parser.parseExpression(floatExpr).getValue(Number.class).doubleValue();

            // Check for division by zero (infinity or NaN from float division)
            if (Double.isInfinite(result) || Double.isNaN(result)) {
                return "Error: Division by zero";
            }

            if (result == (long) result) {
                return String.format("Result: %d", (long) result);
            }
            return String.format("Result: %.6f", result);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("/ by zero")) {
                return "Error: Division by zero";
            }
            return "Error evaluating expression: " + e.getMessage();
        }
    }
}
