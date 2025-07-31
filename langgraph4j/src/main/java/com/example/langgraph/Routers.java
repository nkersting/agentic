package com.example.langgraph;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;

import java.util.concurrent.CompletableFuture;

public class Routers {

    /**
     * Decides the next step based on the story's scariness feedback asynchronously.
     * If SCARY, proceed to funniness check. If NOT_SCARY, go create instructions.
     */
    public static AsyncEdgeAction<State> routeAfterScaryCheck() {
        return state -> CompletableFuture.supplyAsync(() -> {
            int retryCount = state.getRetryCount();
            int maxRetries = state.getMaxRetries();
            String scaryFeedback = state.getScaryCheckFeedback();

            System.out.printf("\n--- Router: routeAfterScaryCheck --- (Current retry: %d/%d)\n", retryCount, maxRetries);
            System.out.printf("  Scary feedback: %s\n", scaryFeedback);

            if ("SCARY".equals(scaryFeedback)) {
                System.out.println("  Story is SCARY. Proceeding to funniness check.");
                return "checkStoryFunnyNode";
            } else { // If NOT_SCARY or any unexpected feedback, always try to regenerate story with instructions
                System.out.println("  Story is NOT_SCARY or feedback is unexpected. Generating instructions for story revision.");
                return "createStoryInstructionsNode";
            }
        });
    }

    /**
     * Decides whether to continue regenerating the story or end if max retries reached asynchronously,
     * based on the terminateFlag set by createStoryInstructions.
     */
    public static AsyncEdgeAction<State> routeAfterCreateInstructions() {
        return state -> CompletableFuture.supplyAsync(() -> {
            int retryCount = state.getRetryCount();
            int maxRetries = state.getMaxRetries();
            boolean terminateFlag = state.isTerminateFlag();

            System.out.printf("\n--- Router: routeAfterCreateInstructions --- (Current retry: %d/%d)\n", retryCount, maxRetries);
            if (terminateFlag) {
                System.out.println("  Terminate flag set by createStoryInstructions. Ending graph.");
                return StateGraph.END;
            } else {
                System.out.println("  Max retries not reached. Re-generating story with new instructions.");
                return "storyNode";
            }
        });
    }

   /**
     * Decides the next step based on the story's funniness feedback asynchronously.
     * If funny, end. If not funny, go create instructions.
     */
    public static AsyncEdgeAction<State> routeAfterFunninessCheck() {
        return state -> CompletableFuture.supplyAsync(() -> {
            int retryCount = state.getRetryCount();
            int maxRetries = state.getMaxRetries();
            String funnyFeedback = state.getFunninessCheckFeedback();

            System.out.printf("\n--- Router: routeAfterFunninessCheck --- (Current retry: %d/%d)\n", retryCount, maxRetries);
            System.out.printf("  Funniness feedback: %s\n", funnyFeedback);

            if ("FUNNY".equals(funnyFeedback)) {
                System.out.println("  Story is FUNNY. Ending graph.");
                return StateGraph.END;
            } else { // If NOT_FUNNY or any unexpected feedback, always try to regenerate story with instructions
                System.out.println("  Story is NOT_FUNNY or feedback is unexpected. Generating instructions for story revision.");
                return "createStoryInstructionsNode";
            }
        });
    }
}