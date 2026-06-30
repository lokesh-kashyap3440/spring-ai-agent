package com.example.aiagent.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(List.of(
                new CalculatorTool(),
                mockTool("weather", "Get weather"),
                mockTool("news", "Get news"),
                mockTool("database", "Query database")
        ));
    }

    private Tool mockTool(String name, String description) {
        return new Tool() {
            @Override
            public String getName() { return name; }

            @Override
            public String getDescription() { return description; }

            @Override
            public String execute(String input) { return "result"; }
        };
    }

    @Test
    void testAllToolsRegistered() {
        Map<String, Tool> all = registry.getAllTools();
        assertEquals(4, all.size());
        assertTrue(all.containsKey("calculator"));
        assertTrue(all.containsKey("weather"));
        assertTrue(all.containsKey("news"));
        assertTrue(all.containsKey("database"));
    }

    @Test
    void testGetToolCaseInsensitive() {
        assertNotNull(registry.getTool("Calculator"));
        assertNotNull(registry.getTool("CALCULATOR"));
        assertNotNull(registry.getTool("WeAtHer"));
    }

    @Test
    void testGetToolReturnsNullForUnknown() {
        assertNull(registry.getTool("nonexistent"));
    }

    @Test
    void testGetToolDescriptionsAll() {
        String desc = registry.getToolDescriptions();
        assertTrue(desc.contains("- calculator:"));
        assertTrue(desc.contains("- weather:"));
        assertTrue(desc.contains("- news:"));
        assertTrue(desc.contains("- database:"));
    }

    @Test
    void testGetToolDescriptionsFiltered() {
        String desc = registry.getToolDescriptions(Set.of("weather", "news"));
        assertTrue(desc.contains("- weather:"));
        assertTrue(desc.contains("- news:"));
        assertFalse(desc.contains("- calculator:"));
        assertFalse(desc.contains("- database:"));
    }

    @Test
    void testGetToolDescriptionsNullEnabled() {
        String desc = registry.getToolDescriptions((Set<String>) null);
        assertTrue(desc.contains("- calculator:"));
        assertTrue(desc.contains("- weather:"));
        assertTrue(desc.contains("- news:"));
        assertTrue(desc.contains("- database:"));
    }

    @Test
    void testIsToolEnabledNullEnabledSet() {
        assertTrue(registry.isToolEnabled("weather", null));
        assertTrue(registry.isToolEnabled("unknown", null));
    }

    @Test
    void testIsToolEnabledWithSet() {
        Set<String> enabled = Set.of("weather", "news");
        assertTrue(registry.isToolEnabled("weather", enabled));
        assertTrue(registry.isToolEnabled("news", enabled));
        assertFalse(registry.isToolEnabled("calculator", enabled));
    }

    @Test
    void testGetToolNamesAll() {
        String names = registry.getToolNames();
        assertTrue(names.contains("calculator"));
        assertTrue(names.contains("weather"));
    }

    @Test
    void testGetToolNamesFiltered() {
        String names = registry.getToolNames(Set.of("weather"));
        assertEquals("weather", names);
    }

    @Test
    void testGetToolNamesNullEnabled() {
        String names = registry.getToolNames((Set<String>) null);
        assertTrue(names.contains("calculator"));
    }

    @Test
    void testEmptyRegistry() {
        ToolRegistry empty = new ToolRegistry(List.of());
        assertTrue(empty.getAllTools().isEmpty());
        assertEquals("", empty.getToolDescriptions());
        assertEquals("", empty.getToolNames());
    }
}
