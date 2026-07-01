package com.example.aiagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class AiProviderChain implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiProviderChain.class);

    private final NvidiaService nvidiaService;
    private final OllamaService ollamaService;
    private final String provider;

    public AiProviderChain(ObjectProvider<NvidiaService> nvidiaProvider,
                           ObjectProvider<OllamaService> ollamaProvider,
                           @Value("${app.ai.provider:nvidia}") String provider) {
        this.nvidiaService = nvidiaProvider.getIfAvailable();
        this.ollamaService = ollamaProvider.getIfAvailable();
        this.provider = provider;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        AiService provider = resolveProvider();
        if (provider == null) {
            log.error("No AI provider available.");
            return "Error: No AI provider is available.";
        }
        return provider.chat(systemPrompt, userMessage);
    }

    @Override
    public boolean isAvailable() {
        AiService provider = resolveProvider();
        return provider != null && provider.isAvailable();
    }

    private AiService resolveProvider() {
        if ("nvidia".equals(provider) && nvidiaService != null && nvidiaService.isAvailable()) {
            return nvidiaService;
        }
        if ("ollama".equals(provider) && ollamaService != null) {
            return ollamaService;
        }
        if (nvidiaService != null && nvidiaService.isAvailable()) {
            return nvidiaService;
        }
        if (ollamaService != null) {
            return ollamaService;
        }
        return null;
    }
}