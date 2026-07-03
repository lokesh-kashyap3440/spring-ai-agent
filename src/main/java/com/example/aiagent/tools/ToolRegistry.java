package com.example.aiagent.tools;

import java.util.Map;
import java.util.Set;

public interface ToolRegistry {
    Tool getTool(String name);
    Map<String, Tool> getAllTools();
    String getToolDescriptions();
    String getToolDescriptions(Set<String> enabledTools);
    String getToolNames();
    String getToolNames(Set<String> enabledTools);
    boolean isToolEnabled(String name, Set<String> enabledTools);
}
