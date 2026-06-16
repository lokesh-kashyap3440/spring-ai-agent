package com.example.aiagent.tools;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class NewsTool implements Tool {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "news";
    }

    @Override
    public String getDescription() {
        return "Get latest news headlines for a topic. Input: topic (e.g., 'technology' or 'sports')";
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
            return String.format(
                "Simulated news for '%s': 1) Major development in %s reported today. " +
                "2) Breaking: New trends emerge in %s sector. " +
                "3) Analysis: Current state of %s market.",
                input.trim(), input.trim(), input.trim(), input.trim()
            );
        }
    }
}
