package com.bookstore.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "assistant")
public class AssistantSettings {
    private String baseUrl = "http://localhost:11434/v1";
    private String apiKey = "";
    private String model = "qwen3:8b";
    private int timeoutSeconds = 90;
    private int maxRounds = 5;
    private String competitorUrl = "http://127.0.0.1:8091/prices";
    private int competitorTimeoutMs = 1500;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int value) { maxRounds = value; }
    public String getCompetitorUrl() { return competitorUrl; }
    public void setCompetitorUrl(String value) { competitorUrl = value; }
    public int getCompetitorTimeoutMs() { return competitorTimeoutMs; }
    public void setCompetitorTimeoutMs(int value) { competitorTimeoutMs = value; }
}
