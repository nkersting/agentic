package com.example.langgraph;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;

// Try these imports - one of them should work
// import org.bsc.langgraph4j.server.jetty.*;
// import org.bsc.langgraph4j.jetty.*;
//import org.bsc.langgraph4j.studio.jetty.*;

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
        
        // Let's try to use reflection to find and instantiate the server
        try {
            System.out.println("Attempting to start Jetty Studio Server...");
            
            // Try Method 1: Look for main classes in the jetty artifact
            tryStartServer1(compiledGraph);
            
        } catch (Exception e) {
            System.err.println("Method 1 failed: " + e.getMessage());
            
            try {
                // Try Method 2: Look for server classes
                tryStartServer2(compiledGraph);
                
            } catch (Exception e2) {
                System.err.println("Method 2 failed: " + e2.getMessage());
                
               
            }
        }
    }
    
    private static void tryStartServer1(CompiledGraph<State> graph) throws Exception {
        // Look for a main class in the jetty server artifact
        Class<?> mainClass = Class.forName("org.bsc.langgraph4j.server.jetty.Main");
        var main = mainClass.getMethod("main", String[].class);
        main.invoke(null, (Object) new String[]{"--port", "8080"});
    }
    
    private static void tryStartServer2(CompiledGraph<State> graph) throws Exception {
        // Look for a server class
        Class<?> serverClass = Class.forName("org.bsc.langgraph4j.server.jetty.LangGraphServer");
        var constructor = serverClass.getConstructor(int.class);
        Object server = constructor.newInstance(8080);
        
        // Try to register the graph
        var registerMethod = serverClass.getMethod("registerGraph", String.class, CompiledGraph.class);
        registerMethod.invoke(server, "story-generator", graph);
        
        // Start the server
        var startMethod = serverClass.getMethod("start");
        startMethod.invoke(server);
        
        System.out.println("Jetty Studio Server started on http://localhost:8080");
        
        // Keep it running
        Thread.currentThread().join();
    }
    
}