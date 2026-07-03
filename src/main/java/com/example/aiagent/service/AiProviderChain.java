package com.example.aiagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Primary
public class AiProviderChain implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderChain.class);

    private final List<AiService> providers;
    private final String configuredProvider;
    private final AtomicInteger totalPromptTokens = new AtomicInteger(0);
    private final AtomicInteger totalCompletionTokens = new AtomicInteger(0);

    public AiProviderChain(List<AiService> providers,
                           @Value("${app.ai.provider:nvidia}") String configuredProvider) {
        this.providers = providers;
        this.configuredProvider = configuredProvider;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        AiService provider = resolveProvider();
        if (provider == null) {
            log.error("No AI provider available.");
            return "Error: No AI provider is available.";
        }
        log.info("Using AI provider: {}", provider.getClass().getSimpleName());
        String response = provider.chat(systemPrompt, userMessage);
        // Accumulate token usage from the underlying provider
        totalPromptTokens.addAndGet(provider.getLastPromptTokens());
        totalCompletionTokens.addAndGet(provider.getLastCompletionTokens());
        return response;
    }

    @Override
    public boolean isAvailable() {
        AiService provider = resolveProvider();
        return provider != null && provider.isAvailable();
    }

    public int getTotalPromptTokens() {
        return totalPromptTokens.get();
    }

    public int getTotalCompletionTokens() {
        return totalCompletionTokens.get();
    }

    public int getTotalTokens() {
        return totalPromptTokens.get() + totalCompletionTokens.get();
    }

    private AiService resolveProvider() {
        // First, try the explicitly configured provider
        AiService configured = findProviderBySimpleName(configuredProvider);
        if (configured != null && configured.isAvailable()) {
            return configured;
        }

        // Fallback: return the first available provider
        for (AiService provider : providers) {
            if (provider != this && provider.isAvailable()) {
                return provider;
            }
        }
        return null;
    }

    private AiService findProviderBySimpleName(String name) {
        String lower = name.toLowerCase();
        for (AiService provider : providers) {
            if (provider != this) {
                String simpleName = provider.getClass().getSimpleName().toLowerCase();
                if (simpleName.contains(lower) || simpleName.equals(lower + "service")) {
                    return provider;
                }
            }
        }
        return null;
    }
}