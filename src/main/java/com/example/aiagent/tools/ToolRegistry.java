package com.example.aiagent.tools;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            tools.put(tool.getName(), tool);
        }
    }

    public Tool getTool(String name) {
        return tools.get(name.toLowerCase());
    }

    public Map<String, Tool> getAllTools() {
        return new HashMap<>(tools);
    }

    public String getToolDescriptions() {
        return tools.values().stream()
            .map(tool -> String.format("- %s: %s", tool.getName(), tool.getDescription()))
            .collect(Collectors.joining("\n"));
    }

    public String getToolNames() {
        return String.join(", ", tools.keySet());
    }
}
