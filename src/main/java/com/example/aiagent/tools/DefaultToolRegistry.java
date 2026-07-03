package com.example.aiagent.tools;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();
    private final Counter getToolCounter;

    public DefaultToolRegistry(List<Tool> toolList, MeterRegistry meterRegistry) {
        for (Tool tool : toolList) {
            tools.put(tool.getName().toLowerCase(), tool);
        }
        this.getToolCounter = Counter.builder("tool.registry.lookups")
                .description("Number of tool lookups via ToolRegistry.getTool()")
                .register(meterRegistry);
    }

    @Override
    public Tool getTool(String name) {
        getToolCounter.increment();
        return tools.get(name.toLowerCase());
    }

    @Override
    public Map<String, Tool> getAllTools() {
        return new HashMap<>(tools);
    }

    @Override
    public String getToolDescriptions() {
        return tools.values().stream()
                .map(tool -> String.format("- %s: %s", tool.getName(), tool.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public String getToolDescriptions(Set<String> enabledTools) {
        if (enabledTools == null) {
            return getToolDescriptions();
        }
        return tools.entrySet().stream()
                .filter(e -> enabledTools.contains(e.getKey()))
                .map(e -> String.format("- %s: %s", e.getKey(), e.getValue().getDescription()))
                .collect(Collectors.joining("\n"));
    }

    @Override
    public boolean isToolEnabled(String name, Set<String> enabledTools) {
        return enabledTools == null || enabledTools.contains(name.toLowerCase());
    }

    @Override
    public String getToolNames() {
        return String.join(", ", tools.keySet());
    }

    @Override
    public String getToolNames(Set<String> enabledTools) {
        if (enabledTools == null) {
            return getToolNames();
        }
        return tools.keySet().stream()
                .filter(t -> enabledTools.contains(t.toLowerCase()))
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
