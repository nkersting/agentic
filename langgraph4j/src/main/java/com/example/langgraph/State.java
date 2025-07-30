package com.example.langgraph;

import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;

public class State extends AgentState {
    
    // Constructor
    public State(Map<String, Object> initData) {
        super(initData);
    }
    
    // Getters with default values
    public String getQuestion() {
        return this.<String>value("question").orElse("");
    }
    
    public String getAnswer() {
        return this.<String>value("answer").orElse("");
    }
    
    public String getStory() {
        return this.<String>value("story").orElse("");
    }
    
    public String getScaryCheckFeedback() {
        return this.<String>value("scaryCheckFeedback").orElse("");
    }
    
    public String getFunninessCheckFeedback() {
        return this.<String>value("funninessCheckFeedback").orElse("");
    }
    
    public String getStoryGenerationInstructions() {
        return this.<String>value("storyGenerationInstructions").orElse("");
    }
    
    public int getRetryCount() {
        return this.<Integer>value("retryCount").orElse(0);
    }
    
    public int getMaxRetries() {
        return this.<Integer>value("maxRetries").orElse(5);
    }
    
    public boolean isTerminateFlag() {
        return this.<Boolean>value("terminateFlag").orElse(false);
    }
    
    // Factory method for creating new instances
    public static State of(String question) {
        return new State(Map.of(
            "question", question,
            "answer", "",
            "story", "",
            "scaryCheckFeedback", "",
            "funninessCheckFeedback", "",
            "storyGenerationInstructions", "",
            "retryCount", 0,
            "maxRetries", 5,
            "terminateFlag", false
        ));
    }
    
    // Method to update values in the state
    public State appendValue(String key, Object value) {
        this.data().put(key, value);
        return this;
    }
}