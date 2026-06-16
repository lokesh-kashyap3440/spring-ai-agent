package com.example.aiagent.tools;

public interface Tool {

    String getName();

    String getDescription();

    String execute(String input);

    default String getParameterSchema() {
        return "{\"type\": \"string\", \"description\": \"Input for " + getName() + "\"}";
    }
}
