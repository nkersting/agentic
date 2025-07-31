package com.example.langgraph;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.GraphStateException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public class Main {
    public static void main(String[] args) {
        try {
            // Ensure ANTHROPIC_API_KEY environment variable is set
            if (System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("ANTHROPIC_API_KEY").isEmpty()) {
                throw new IllegalArgumentException("ANTHROPIC_API_KEY environment variable not set.");
            }

            // --- Define the graph ---
            // Use State class
            StateGraph<State> graph = new StateGraph<>(State::new);

            // Add nodes using AsyncNodeAction
            
            graph.addNode("claudeNode", Nodes::callClaude);
            graph.addNode("storyNode", Nodes::generateStory);
            graph.addNode("checkScaryNode", Nodes::checkStoryScary);
            graph.addNode("checkStoryFunnyNode", Nodes::checkStoryFunny);
            graph.addNode("createStoryInstructionsNode", Nodes::createStoryInstructions);

            graph.addEdge(StateGraph.START, "storyNode");
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
            RunnableConfig runnableConfig = RunnableConfig.builder().build();
            
            // Convert initial state to Map<String, Object> for invoke method
            Map<String, Object> initialStateMap = new HashMap<>();
            initialStateMap.put("question", initialState.getQuestion());
            initialStateMap.put("answer", initialState.getAnswer() != null ? initialState.getAnswer() : "");
            initialStateMap.put("story", initialState.getStory() != null ? initialState.getStory() : "");
            initialStateMap.put("scaryCheckFeedback", initialState.getScaryCheckFeedback() != null ? initialState.getScaryCheckFeedback() : "");
            initialStateMap.put("funninessCheckFeedback", initialState.getFunninessCheckFeedback() != null ? initialState.getFunninessCheckFeedback() : "");
            initialStateMap.put("storyGenerationInstructions", initialState.getStoryGenerationInstructions() != null ? initialState.getStoryGenerationInstructions() : "");
            initialStateMap.put("retryCount", initialState.getRetryCount());
            initialStateMap.put("maxRetries", initialState.getMaxRetries());
            initialStateMap.put("terminateFlag", initialState.isTerminateFlag());

            
            Optional<State> optionalState = compiledGraph.invoke(initialStateMap, runnableConfig);
            
            if (optionalState.isPresent()) {
                State finalState = optionalState.get();

           

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
            } else {
                System.err.println("Graph execution completed but returned no final state.");
            }

        } catch (GraphStateException e) {
            System.err.println("Graph state error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}