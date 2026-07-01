package com.example.aiagent.tools;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WeatherTool implements Tool {

    private final RestTemplate restTemplate;

    public WeatherTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getDescription() {
        return "Get current weather for a city. Input: city name (e.g., 'London' or 'New York')";
    }

    @Override
    public String execute(String input) {
        try {
            String city = input.trim();
            String url = String.format(
                    "https://wttr.in/%s?format=%%t+%%C+%%h+%%w",
                    city.replace(" ", "+")
            );
            String result = restTemplate.getForObject(url, String.class);
            return String.format("Weather for %s: %s", city, result);
        } catch (Exception e) {
            return "Unable to fetch weather. Using simulated data: Sunny, 25°C, 45% humidity, 10 km/h wind";
        }
    }
}
