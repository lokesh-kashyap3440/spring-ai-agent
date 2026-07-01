package com.example.aiagent.tools;

import org.springframework.stereotype.Component;

/**
 * A tool that evaluates basic mathematical expressions using recursive descent parsing.
 *
 * <p>Supported operations: addition (+), subtraction (-), multiplication (*), division (/),
 * and square root (sqrt). The evaluator processes operators left-to-right without
 * standard mathematical precedence (e.g., {@code 2 + 3 * 4} evaluates as {@code 20},
 * not {@code 14}). Parentheses and negative number literals are not supported.</p>
 */
@Component
public class CalculatorTool implements Tool {

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "Evaluate mathematical expressions. Input: math expression (e.g., '2 + 2' or 'sqrt(16)')";
    }

    @Override
    public String execute(String input) {
        try {
            String expression = input.trim();
            double result = evaluate(expression);
            if (result == (long) result) {
                return String.format("Result: %d", (long) result);
            }
            return String.format("Result: %.6f", result);
        } catch (Exception e) {
            return "Error evaluating expression: " + e.getMessage();
        }
    }

    /**
     * Recursively evaluates a mathematical expression by splitting on operators
     * from lowest to highest precedence (left-to-right, no standard precedence).
     *
     * <p>The algorithm works by finding the rightmost operator of each type
     * and splitting the expression there, then recursively evaluating both sides.
     * This gives left-to-right evaluation order.</p>
     *
     * @param expression the mathematical expression to evaluate (whitespace is stripped)
     * @return the numeric result
     * @throws NumberFormatException if the expression is not a valid number
     * @throws ArithmeticException   if division by zero is attempted
     */
    private double evaluate(String expression) {
        expression = expression.replaceAll("\\s+", "");

        if (expression.contains("+")) {
            String[] parts = expression.split("\\+");
            return evaluate(parts[0]) + evaluate(parts[1]);
        }
        if (expression.contains("-") && !expression.startsWith("-")) {
            String[] parts = expression.split("-");
            return evaluate(parts[0]) - evaluate(parts[1]);
        }
        if (expression.contains("*")) {
            String[] parts = expression.split("\\*");
            return evaluate(parts[0]) * evaluate(parts[1]);
        }
        if (expression.contains("/")) {
            String[] parts = expression.split("/");
            double divisor = evaluate(parts[1]);
            if (divisor == 0) throw new ArithmeticException("Division by zero");
            return evaluate(parts[0]) / divisor;
        }
        if (expression.startsWith("sqrt(") && expression.endsWith(")")) {
            return evaluateSqrt(expression.substring(5, expression.length() - 1));
        }

        return Double.parseDouble(expression);
    }

    private double evaluateSqrt(String innerExpression) {
        double val = evaluate(innerExpression);
        if (val < 0) throw new ArithmeticException("Square root of negative number");
        return Math.sqrt(val);
    }
}
