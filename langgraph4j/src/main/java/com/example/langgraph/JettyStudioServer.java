package com.example.langgraph;


import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;

import java.util.Map;

public class JettyStudioServer {
    
    public static void main(String[] args) throws Exception {
        // Ensure ANTHROPIC_API_KEY environment variable is set
        if (System.getenv("ANTHROPIC_API_KEY") == null || System.getenv("ANTHROPIC_API_KEY").isEmpty()) {
            throw new IllegalArgumentException("ANTHROPIC_API_KEY environment variable not set.");
        }

        // Create the graph (same as in Main.java)
        StateGraph<State> graph = new StateGraph<>(State::new);

        // Add nodes
        graph.addNode("claudeNode", Nodes::callClaude);
        graph.addNode("storyNode", Nodes::generateStory);
        graph.addNode("checkScaryNode", Nodes::checkStoryScary);
        graph.addNode("checkStoryFunnyNode", Nodes::checkStoryFunny);
        graph.addNode("createStoryInstructionsNode", Nodes::createStoryInstructions);

        // Add edges
        graph.addEdge(StateGraph.START, "storyNode");
        graph.addEdge("claudeNode", "storyNode");
        graph.addEdge("storyNode", "checkScaryNode");

        // Conditional edges
        graph.addConditionalEdges(
                "checkScaryNode",
                Routers.routeAfterScaryCheck(),
                Map.of(
                        "checkStoryFunnyNode", "checkStoryFunnyNode",
                        "createStoryInstructionsNode", "createStoryInstructionsNode"
                )
        );

        graph.addConditionalEdges(
                "createStoryInstructionsNode",
                Routers.routeAfterCreateInstructions(),
                Map.of(
                        "storyNode", "storyNode",
                        StateGraph.END, StateGraph.END
                )
        );

        graph.addConditionalEdges(
                "checkStoryFunnyNode",
                Routers.routeAfterFunninessCheck(),
                Map.of(
                        "createStoryInstructionsNode", "createStoryInstructionsNode",
                        StateGraph.END, StateGraph.END
                )
        );

        CompiledGraph<State> compiledGraph = graph.compile();
        
        System.out.println("Graph compiled successfully!");
        
        // Try to find and use the Jetty studio classes
        // We'll try different possible import patterns
        
        try {
            // Method 1: Try using reflection to find available classes
            System.out.println("Looking for Jetty Studio classes...");
            
            // Let's try some common class names that might exist
            tryInstantiateStudioServer1(compiledGraph);
            
        } catch (Exception e) {
            System.err.println("Method 1 failed: " + e.getMessage());
            
            try {
                tryInstantiateStudioServer2(compiledGraph);
            } catch (Exception e2) {
                System.err.println("Method 2 failed: " + e2.getMessage());
                
                try {
                    tryInstantiateStudioServer3(compiledGraph);
                } catch (Exception e3) {
                    System.err.println("Method 3 failed: " + e3.getMessage());
                    System.out.println("All methods failed. The Jetty studio might need a different approach.");
                    System.out.println("Try using your IDE's autocomplete with 'org.bsc.langgraph4j.' to see available classes.");
                }
            }
        }
    }
    
    private static void tryInstantiateStudioServer1(CompiledGraph<State> graph) throws Exception {
        // Try common package naming patterns
        Class<?> serverClass = Class.forName("org.bsc.langgraph4j.server.jetty.LangGraphServer");
        System.out.println("Found class: " + serverClass.getName());
        // We'd instantiate and use it here if found
    }
    
    private static void tryInstantiateStudioServer2(CompiledGraph<State> graph) throws Exception {
        // Try alternative naming
        Class<?> serverClass = Class.forName("org.bsc.langgraph4j.jetty.StudioServer");
        System.out.println("Found class: " + serverClass.getName());
    }
    
    private static void tryInstantiateStudioServer3(CompiledGraph<State> graph) throws Exception {
        // Try another alternative
        Class<?> serverClass = Class.forName("org.bsc.langgraph4j.studio.jetty.Server");
        System.out.println("Found class: " + serverClass.getName());
    }
}