package com.example.aiagent.tools;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class NewsTool implements Tool {

    private final RestTemplate restTemplate;

    public NewsTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "news";
    }

    @Override
    public String getDescription() {
        return "Get latest news headlines for a topic. Input: topic (e.g., 'technology' or 'sports')";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "topic", Map.of("type", "string", "description", "Topic name (e.g., technology)")
            ),
            "required", List.of("topic")
        );
    }

    @Override
    public String execute(String input) {
        try {
            String topic = input.trim().toLowerCase();
            String url = String.format(
                    "https://newsapi.org/v2/top-headlines?q=%s&pageSize=3&apiKey=demo",
                    topic.replace(" ", "+")
            );
            String result = restTemplate.getForObject(url, String.class);
            return result;
        } catch (Exception e) {
            return "Simulated news for '" + input.trim().toLowerCase()
                    + "' - Headline 1: Breaking news in " + input.trim().toLowerCase()
                    + ". Headline 2: Latest updates from " + input.trim().toLowerCase()
                    + ". Headline 3: " + input.trim().toLowerCase() + " market report.";
        }
    }
}
