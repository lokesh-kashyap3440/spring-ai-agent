package com.example.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.agent")
public class AgentConfig {

    private int maxIterations = 10;
    private int memorySize = 20;

    public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
    public int getMemorySize() { return memorySize; }
    public void setMemorySize(int memorySize) { this.memorySize = memorySize; }
}
