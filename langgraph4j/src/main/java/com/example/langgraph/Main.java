package com.example.langgraph;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // Ensure ANTHROPIC_API_KEY environment variable is set
        if (System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("ANTHROPIC_API_KEY").isEmpty()) {
            throw new IllegalArgumentException("ANTHROPIC_API_KEY environment variable not set.");
        }

        // --- Define the graph ---
        // Use State class
        StateGraph<State> graph = new StateGraph<>(State::new);

        // Add nodes using AsyncNodeAction
        graph.addNode("claudeNode", AsyncNodeAction.of(Nodes::callClaude));
        graph.addNode("storyNode", AsyncNodeAction.of(Nodes::generateStory));
        graph.addNode("checkScaryNode", AsyncNodeAction.of(Nodes::checkStoryScary));
        graph.addNode("checkStoryFunnyNode", AsyncNodeAction.of(Nodes::checkStoryFunny));
        graph.addNode("createStoryInstructionsNode", AsyncNodeAction.of(Nodes::createStoryInstructions));

        graph.setEntryPoint("claudeNode");
        graph.addEdge("claudeNode", "storyNode");
        graph.addEdge("storyNode", "checkScaryNode");

        // Conditional edge after scary check
        graph.addConditionalEdges(
                "checkScaryNode",
                Routers.routeAfterScaryCheck(),
                Map.of(
                        "checkStoryFunnyNode", "checkStoryFunnyNode", // If SCARY, go to funniness check
                        "createStoryInstructionsNode", "createStoryInstructionsNode" // If NOT_SCARY, go to generate instructions
                )
        );

        // Conditional edge after creating instructions (decides whether to loop or end)
        graph.addConditionalEdges(
                "createStoryInstructionsNode",
                Routers.routeAfterCreateInstructions(),
                Map.of(
                        "storyNode", "storyNode", // Loop back to generate story with instructions
                        StateGraph.END, StateGraph.END // If max retries reached
                )
        );

        // Conditional edge after funniness check
        graph.addConditionalEdges(
                "checkStoryFunnyNode",
                Routers.routeAfterFunninessCheck(),
                Map.of(
                        "createStoryInstructionsNode", "createStoryInstructionsNode", // If NOT_FUNNY, go to generate instructions
                        StateGraph.END, StateGraph.END // If FUNNY
                )
        );

        var compiledGraph = graph.compile();
        System.out.println("Graph compiled successfully: Question Answer Story with Feedback-Driven Retries");

        // --- Run the example ---
        String initialQuestion = "Why did the sentient shadow always carry a rubber duck into the haunted attic?";

        // Create initial state using State class
        State initialState = State.of(initialQuestion);

        Configuration configForRun = Configuration.builder()
                .temperature(0.5)
                .modelName("claude-3-haiku-20240307")
                .storyTemperature(0.9)
                .maxRetries(5) // Set overall max_retries for story generation attempts
                .build();

        // Pass configurable parameters via config map
        Map<String, Object> config = new HashMap<>();
        config.put("configurable", configForRun);

        System.out.println("\n--- Starting Graph Run ---");
        System.out.printf("Initial state: %s\n", initialState);
        System.out.printf("Running graph with config: %s\n", configForRun);

        // LangGraph4j does not have a direct equivalent of `astream` for printing chunks
        // You would typically use LangSmith for live tracing.
        // For now, we'll just run `invoke` and print the final state.
        System.out.println("\n--- Running Graph (synchronously for final state) ---");
        State finalState = compiledGraph.invoke(initialState, config).get(); // .get() blocks until future completes

        System.out.println("\n--- Final Graph State ---");
        System.out.printf("\nFinal State (after full run): %s\n", finalState);
        System.out.printf("\nClaude's Answer: %s\n", finalState.getAnswer());
        System.out.printf("\nGenerated Story:\n%s\n", finalState.getStory());
        System.out.printf("\nStory Scary Check Feedback: %s\n", finalState.getScaryCheckFeedback());
        System.out.printf("\nStory Funniness Check Feedback: %s\n", finalState.getFunninessCheckFeedback());
        System.out.printf("Total Retries: %d\n", finalState.getRetryCount());
        String instructions = finalState.getStoryGenerationInstructions();
        if (instructions != null && !instructions.isEmpty()) {
            System.out.printf("\nRemaining Story Generation Instructions (if any): %s\n", instructions);
        }
    }
}