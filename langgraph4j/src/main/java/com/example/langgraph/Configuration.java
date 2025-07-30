package com.example.langgraph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuration {
    private double temperature;
    private String modelName;
    private double storyTemperature;
    private int maxRetries; // Overall max retries for any loop
}

