package com.example.aiagent.tools;

import java.util.List;
import java.util.Map;

public interface Tool {

    String getName();

    String getDescription();

    String execute(String input);

    default Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "input", Map.of("type", "string", "description", "Input for " + getName())
            ),
            "required", List.of("input")
        );
    }
}
