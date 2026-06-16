package com.example.aiagent.tools;

import org.springframework.stereotype.Component;

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
            double val = evaluate(expression.substring(5, expression.length() - 1));
            return Math.sqrt(val);
        }

        return Double.parseDouble(expression);
    }
}
