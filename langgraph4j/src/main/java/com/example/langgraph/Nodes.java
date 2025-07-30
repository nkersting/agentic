package com.example.langgraph;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.concurrent.CompletableFuture;

public class Nodes {

    // Helper to get LLM instance with default config
    private static ChatLanguageModel getChatModel(String defaultModel, double defaultTemperature) {
        return AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(defaultModel)
                .temperature(defaultTemperature)
                .build();
    }

    // Helper to get story LLM instance with default config
    private static ChatLanguageModel getStoryChatModel(String defaultModel, double defaultTemperature) {
        return AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(defaultModel)
                .temperature(defaultTemperature)
                .build();
    }

    /**
     * Processes the user's question using Anthropic's Claude.
     */
    public static CompletableFuture<State> callClaude(State state) {
        return CompletableFuture.supplyAsync(() -> {
            ChatLanguageModel llm = getChatModel("claude-3-haiku-20240307", 0.7);

            String question = state.getQuestion();
            
            if (question == null || question.isEmpty()) {
                System.out.println("No question provided, skipping Claude answer generation.");
                state.appendValue("answer", "No question was asked due to empty input.");
                return state;
            }

            int retryCount = state.getRetryCount();
            System.out.printf("\n--- Node: callClaude --- (Attempt %d)\n", retryCount + 1);
            System.out.printf("  Calling Claude for answer with question: '%s'\n", question);

            // LangChain4j's generate method takes messages directly
            String response = llm.generate(question);

            System.out.printf("  Received answer from Claude: %s\n", response);

            state.appendValue("answer", response);
            return state;
        });
    }

    /**
     * Generates a short 2-paragraph story using the question and answer,
     * optionally guided by specific instructions.
     */
    public static CompletableFuture<State> generateStory(State state) {
        return CompletableFuture.supplyAsync(() -> {
            ChatLanguageModel llm = getStoryChatModel("claude-3-haiku-20240307", 0.9);

            String question = state.getQuestion();
            String answer = state.getAnswer();
            String storyInstructions = state.getStoryGenerationInstructions();
            int retryCount = state.getRetryCount();

            String systemMessageContent;
            String humanMessageContent;

            if (storyInstructions != null && !storyInstructions.isEmpty()) {
                systemMessageContent =
                        "You are a whimsical storyteller. Create a short, two-paragraph story." +
                        "You have received specific instructions for this new version of the story. " +
                        "Prioritize these instructions to make the story better. " +
                        "The story must feature the following question and its answer as the central punchline " +
                        "or a key revelation. Make it engaging and concise.";
                humanMessageContent =
                        "Based on these instructions: '" + storyInstructions + "', " +
                        "and the original question: '" + question + "', and answer: '" + answer + "', " +
                        "write a new story.";
                System.out.printf("  Generating story with instructions: '%s...'\n", storyInstructions.substring(0, Math.min(storyInstructions.length(), 70)));
            } else {
                systemMessageContent =
                        "You are a whimsical storyteller. Create a short, two-paragraph story." +
                        "The story must feature the following question and its answer as the central punchline " +
                        "or a key revelation. Make it engaging and concise.";
                humanMessageContent =
                        "The question was: '" + question + "'\n" +
                        "The answer was: '" + answer + "'\n\n" +
                        "Now, write the story.";
                System.out.printf("  Generating initial story based on: '%s' and '%s'\n", question, answer);
            }

            System.out.printf("\n--- Node: generateStory --- (Attempt %d)\n", retryCount + 1);
            System.out.println("  State received by generateStory: ");
            System.out.printf("    Question: '%s'\n", question);
            System.out.printf("    Answer: '%s'\n", answer);

            String storyResponse = llm.generate(systemMessageContent + "\n\n" + humanMessageContent);

            System.out.printf("  Generated story (first 100 chars): %s...\n", storyResponse.substring(0, Math.min(storyResponse.length(), 100)));

            // Clear instructions after using them for this generation
            state.appendValue("story", storyResponse);
            state.appendValue("storyGenerationInstructions", "");
            return state;
        });
    }

    /**
     * Uses an LLM to evaluate if the generated story is scary and meets the criteria.
     * Returns reason for scary or not scary.
     */
    public static CompletableFuture<State> checkStoryScary(State state) {
        return CompletableFuture.supplyAsync(() -> {
            ChatLanguageModel llm = getChatModel("claude-3-haiku-20240307", 0.0);

            String question = state.getQuestion();
            String answer = state.getAnswer();
            String story = state.getStory();
            int retryCount = state.getRetryCount();

            String systemMessage = 
                    "You are an AI assistant tasked with evaluating stories for their scare factor. " +
                    "Your goal is to determine if a given story is genuinely scary, creepy, or unsettling. " +
                    "It should evoke a sense of dread, fear, or suspense. " +
                    "Respond with ONLY 'SCARY' if it meets this criterion, otherwise respond with ONLY 'NOT_SCARY'. " +
                    "Do NOT add any other text or explanation. Your output must be one word." +
                    "Consider these points:\n" +
                    "- Does it create a chilling atmosphere?\n" +
                    "- Is there genuine suspense or horror?\n" +
                    "- Does it evoke fear or discomfort?\n" +
                    "- Is the provided question and answer integrated into the scary narrative?\n";

            String userMessage = 
                    "Here is the question: '" + question + "'\n" +
                    "Here is the answer: '" + answer + "'\n" +
                    "Here is the story: '" + story + "'\n\n" +
                    "Is this story scary? Respond with 'SCARY' or 'NOT_SCARY'.";

            System.out.printf("\n--- Node: checkScaryNode --- (Attempt %d)\n", retryCount + 1);
            System.out.printf("  Checking scariness of story (first 50 chars): '%s...'\n", story.substring(0, Math.min(story.length(), 50)));

            String evaluationResponse = llm.generate(systemMessage + "\n\n" + userMessage);
            String feedback = evaluationResponse.trim().toUpperCase();

            System.out.printf("  Story scariness feedback: %s\n", feedback);

            state.appendValue("scaryCheckFeedback", feedback);
            return state;
        });
    }

    /**
     * Uses an LLM to evaluate if the generated story is funny.
     * Returns reason for funny or not funny.
     */
    public static CompletableFuture<State> checkStoryFunny(State state) {
        return CompletableFuture.supplyAsync(() -> {
            ChatLanguageModel llm = getChatModel("claude-3-haiku-20240307", 0.0); // Low temp for strict evaluation

            String question = state.getQuestion();
            String answer = state.getAnswer();
            String story = state.getStory();
            int retryCount = state.getRetryCount();

            String systemMessage = 
                    "You are an AI comedian evaluator. Your task is to determine if the given story " +
                    "is genuinely funny, amusing, or has a clear comedic element, especially considering " +
                    "how the question and answer were used. " +
                    "Respond with ONLY 'FUNNY' if it achieves this, otherwise respond with ONLY 'NOT_FUNNY'. " +
                    "Do NOT add any other text or explanation. Your output must be one word.";

            String userMessage = 
                    "Question: '" + question + "'\n" +
                    "Answer: '" + answer + "'\n" +
                    "Story: '" + story + "'\n\n" +
                    "Is this story funny? Respond 'FUNNY' or 'NOT_FUNNY' and then explicitly why you think so.";

            System.out.printf("\n--- Node: checkStoryFunny --- (Attempt %d)\n", retryCount + 1);
            System.out.printf("  Checking funniness of story (first 50 chars): '%s...'\n", story.substring(0, Math.min(story.length(), 50)));

            String evaluationResponse = llm.generate(systemMessage + "\n\n" + userMessage);
            String feedback = evaluationResponse.trim().toUpperCase();
            
            System.out.printf("  Story funniness feedback: %s\n", feedback);

            state.appendValue("funninessCheckFeedback", feedback);
            return state;
        });
    }

    /**
     * Generates specific instructions for the story node based on feedback.
     * Increments retry count and sets termination flag if max retries hit.
     */
    public static CompletableFuture<State> createStoryInstructions(State state) {
        return CompletableFuture.supplyAsync(() -> {
            int currentRetryCount = state.getRetryCount() + 1;
            int maxRetries = state.getMaxRetries();
            ChatLanguageModel llm = getChatModel("claude-3-haiku-20240307", 0.5);

            System.out.printf("\n--- Node: createStoryInstructions --- (Attempt %d/%d)\n", currentRetryCount, maxRetries);

            state.appendValue("retryCount", currentRetryCount);

            if (currentRetryCount > maxRetries) {
                System.out.printf("  Max retries (%d) reached for story generation. Setting terminateFlag to True.\n", maxRetries);
                state.appendValue("story", "");
                state.appendValue("scaryCheckFeedback", "");
                state.appendValue("funninessCheckFeedback", "");
                state.appendValue("storyGenerationInstructions", "");
                state.appendValue("terminateFlag", true);
                return state;
            }

            String question = state.getQuestion();
            String answer = state.getAnswer();
            String story = state.getStory();
            String scaryFeedback = state.getScaryCheckFeedback();
            String funnyFeedback = state.getFunninessCheckFeedback();

            String instructions = "";

            // Check if the specific scary feedback indicates it was NOT_SCARY
            if (scaryFeedback != null && scaryFeedback.startsWith("NOT_SCARY")) {
                // Extract the reason/suggestions from the feedback
                String scaryReason = scaryFeedback.replace("NOT_SCARY", "").trim() + " This needs to be more scary.";
                System.out.printf("  Story not scary. Generating instructions based on feedback: '%s'\n", scaryReason);

                String systemMessage = 
                        "You are an AI assistant tasked with converting feedback into concise, actionable instructions " +
                        "for a storyteller. The feedback will explain why a story wasn't scary and provide suggestions. " +
                        "Your goal is to guide the storyteller to revise the story to make it more SCARY, creepy, or suspenseful, " +
                        "without changing the core question and answer. " +
                        "Provide only the instructions, starting directly with the instruction phrase. " +
                        "Keep them focused and brief, no more than 1-2 sentences. " +
                        "Incorporate the provided feedback directly into the instruction.";

                String userMessage = 
                        "The original question was: '" + question + "'\n" +
                        "The answer was: '" + answer + "'\n" +
                        "The current story: '" + story + "'\n" +
                        "Feedback: '" + scaryReason + "'\n\n" +
                        "Generate instructions to make this story scary, incorporating the feedback.";

                instructions = llm.generate(systemMessage + "\n\n" + userMessage).trim();

            } else if (funnyFeedback != null && funnyFeedback.startsWith("NOT_FUNNY")) {
                System.out.println("  Story not funny. Generating instructions to make it funny.");
                
                String systemMessage = 
                        "You are an AI assistant tasked with providing concise, actionable instructions " +
                        "to a storyteller. Your goal is to guide the storyteller to revise a story " +
                        "to make it more FUNNY, amusing, or comedic, without changing the core question and answer. " +
                        "Provide only the instructions, starting directly with the instruction phrase. " +
                        "Keep them focused and brief, no more than 1-2 sentences.";

                String userMessage = 
                        "The original question was: '" + question + "'\n" +
                        "The answer was: '" + answer + "'\n" +
                        "The current story (which was not funny): '" + story + "'\n\n" +
                        "Generate instructions to make this story funny, focusing on comedic timing, absurd situations, or witty dialogue.";

                instructions = llm.generate(systemMessage + "\n\n" + userMessage).trim();
            } else {
                System.out.println("  Neither specific scary nor funny feedback indicating NOT_SCARY/NOT_FUNNY. Generating generic refinement instructions.");
                instructions = "Revise the story to improve its overall quality and integration of the question/answer.";
            }

            System.out.printf("  Generated instructions: '%s'\n", instructions);

            state.appendValue("story", state.getStory()); // Keep current story, it will be regenerated
            state.appendValue("scaryCheckFeedback", ""); // Clear feedback for next loop
            state.appendValue("funninessCheckFeedback", ""); // Clear funniness feedback for next loop
            state.appendValue("storyGenerationInstructions", instructions);
            state.appendValue("terminateFlag", false);
            
            return state;
        });
    }
}