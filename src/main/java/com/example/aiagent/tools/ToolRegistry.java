package com.example.aiagent.tools;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central registry for all available {@link Tool} implementations.
 *
 * <p>Tools are auto-discovered via Spring dependency injection and stored
 * with lowercase-normalized names for case-insensitive lookup. Provides
 * filtering support for enabling/disabling tools per request.</p>
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(List<Tool> toolList) {
        for (Tool tool : toolList) {
            tools.put(tool.getName().toLowerCase(), tool);
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

    public String getToolDescriptions(Set<String> enabledTools) {
        if (enabledTools == null) {
            return getToolDescriptions();
        }
        return tools.entrySet().stream()
                .filter(e -> enabledTools.contains(e.getKey()))
                .map(e -> String.format("- %s: %s", e.getKey(), e.getValue().getDescription()))
                .collect(Collectors.joining("\n"));
    }

    public boolean isToolEnabled(String name, Set<String> enabledTools) {
        return enabledTools == null || enabledTools.contains(name.toLowerCase());
    }

    public String getToolNames() {
        return String.join(", ", tools.keySet());
    }

    public String getToolNames(Set<String> enabledTools) {
        if (enabledTools == null) {
            return getToolNames();
        }
        return String.join(", ", enabledTools.stream().map(String::toLowerCase).toList());
    }
}
