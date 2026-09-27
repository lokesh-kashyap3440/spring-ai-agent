package com.example.aiagent.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Primary
public class AiProviderChain implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderChain.class);

    private final List<ProviderSlot> providers;
    private final String configuredProvider;
    private final AtomicInteger totalPromptTokens = new AtomicInteger(0);
    private final AtomicInteger totalCompletionTokens = new AtomicInteger(0);

    public AiProviderChain(List<AiService> providerList,
                           CircuitBreakerRegistry circuitBreakerRegistry,
                           @Value("${app.ai.provider:lmstudio}") String configuredProvider) {
        if (providerList == null || providerList.isEmpty()) {
            throw new IllegalArgumentException("At least one AiService provider must be configured");
        }
        this.configuredProvider = configuredProvider;

        // Wrap each provider with its own circuit breaker
        this.providers = providerList.stream()
                .filter(p -> p != this)
                .map(p -> {
                    String name = p.getClass().getSimpleName();
                    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
                    return new ProviderSlot(p, cb, name);
                })
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        // Try the configured provider first, then fall back through the rest
        ProviderSlot primary = findPrimaryProvider();
        List<ProviderSlot> fallbackChain = providers.stream()
                .filter(p -> p != primary)
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        java.util.List<ProviderSlot> ordered = new java.util.ArrayList<>();
        if (primary != null) ordered.add(primary);
        ordered.addAll(fallbackChain);

        Exception lastError = null;
        for (ProviderSlot slot : ordered) {
            try {
                String response = slot.call(systemPrompt, userMessage);
                totalPromptTokens.addAndGet(slot.provider.getLastPromptTokens());
                totalCompletionTokens.addAndGet(slot.provider.getLastCompletionTokens());
                return response;
            } catch (CallNotPermittedException e) {
                log.warn("Circuit breaker OPEN for provider '{}', skipping", slot.name);
                lastError = e;
            } catch (Exception e) {
                log.warn("Provider '{}' failed after circuit breaker evaluation: {}", slot.name, e.getMessage());
                lastError = e;
            }
        }

        log.error("All AI providers exhausted. Last error: {}", lastError != null ? lastError.getMessage() : "unknown");
        return "Error: All AI providers are currently unavailable. Please try again later.";
    }

    @Override
    public boolean isAvailable() {
        return providers.stream().anyMatch(slot ->
                slot.circuitBreaker.getState() != CircuitBreaker.State.OPEN && slot.provider.isAvailable());
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

    private ProviderSlot findPrimaryProvider() {
        String lower = configuredProvider.toLowerCase();
        for (ProviderSlot slot : providers) {
            String simpleName = slot.name.toLowerCase();
            if (simpleName.contains(lower) || simpleName.equals(lower + "service") || simpleName.startsWith(lower)) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Wraps an {@link AiService} provider with its associated circuit breaker.
     */
    private record ProviderSlot(AiService provider, CircuitBreaker circuitBreaker, String name) {
        String call(String systemPrompt, String userMessage) {
            return circuitBreaker.executeSupplier(() -> provider.chat(systemPrompt, userMessage));
        }
    }
}