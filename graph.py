import os
from typing import TypedDict, Dict, Any, Literal
from dataclasses import dataclass, field
from langgraph.graph import StateGraph, END
from langchain_core.runnables import RunnableConfig
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_anthropic import ChatAnthropic
from langchain_core.prompts import ChatPromptTemplate

class Configuration(TypedDict):
    """Configurable parameters for the agent."""
    temperature: float
    model_name: str
    story_temperature: float
    max_retries: int # Overall max retries for any loop

@dataclass
class State:
    """Represents the current state of our conversation/workflow."""
    question: str = ""
    answer: str = ""
    story: str = ""
    scary_check_feedback: str = "" # Feedback from scary check
    funniness_check_feedback: str = "" # Feedback from funniness check
    story_generation_instructions: str = "" # NEW: Instructions for story refinement
    retry_count: int = 0 # Overall retry count across all story generation attempts
    max_retries: int = 8 # Default max_retries directly in state
    terminate_flag: bool = False # Flag set by create_story_instructions if max retries hit

async def call_claude(state: State, config: RunnableConfig) -> Dict[str, Any]:
    """
    Processes the user's question using Anthropic's Claude.
    """
    model_name = config.get('configurable', {}).get('model_name', "claude-3-haiku-20240307")
    temperature = config.get('configurable', {}).get('temperature', 0.7)

    llm = ChatAnthropic(model_name=model_name, temperature=temperature)

    if not state.question:
        print("No question provided, skipping Claude answer generation.")
        return {"answer": "No question was asked due to empty input."}

    messages = [HumanMessage(content=state.question)]

    print(f"\n--- Node: call_claude --- (Attempt {state.retry_count + 1})")
    print(f"  Calling Claude for answer with question: '{state.question}'")

    response = await llm.ainvoke(messages)

    print(f"  Received answer from Claude: {response.content}")

    return {"answer": response.content}


# MODIFIED NODE: generate_story to accept instructions
async def generate_story(state: State, config: RunnableConfig) -> Dict[str, Any]:
    """
    Generates a short 2-paragraph story using the question and answer,
    optionally guided by specific instructions.
    """
    model_name = config.get('configurable', {}).get('model_name', "claude-3-haiku-20240307")
    story_temperature = config.get('configurable', {}).get('story_temperature', 0.9)

    llm = ChatAnthropic(model_name=model_name, temperature=story_temperature)

    if state.story_generation_instructions:
        system_message = (
            "You are a whimsical storyteller. Create a short, two-paragraph story."
            "You have received specific instructions for this new version of the story. "
            "Prioritize these instructions to make the story better. "
            "The story must feature the following question and its answer as the central punchline "
            "or a key revelation. Make it engaging and concise."
        )
        human_message_content = (
            f"Based on these instructions: '{state.story_generation_instructions}', "
            f"and the original question: '{state.question}', and answer: '{state.answer}', "
            "write a new story."
        )
        prompt_input = {
            "story_generation_instructions": state.story_generation_instructions,
            "question": state.question,
            "answer": state.answer
        }
        print(f"  Generating story with instructions: '{state.story_generation_instructions[:70]}...'")
    else:
        system_message = (
            "You are a whimsical storyteller. Create a short, two-paragraph story."
            "The story must feature the following question and its answer as the central punchline "
            "or a key revelation. Make it engaging and concise."
        )
        human_message_content = (
            f"The question was: '{state.question}'\n"
            f"The answer was: '{state.answer}'\n\n"
            "Now, write the story."
        )
        prompt_input = {
            "question": state.question,
            "answer": state.answer
        }
        print(f"  Generating initial story based on: '{state.question}' and '{state.answer}'")


    story_prompt_template = ChatPromptTemplate.from_messages(
        [
            SystemMessage(system_message),
            HumanMessage(human_message_content)
        ]
    )

    story_chain = story_prompt_template | llm

    print(f"\n--- Node: generate_story --- (Attempt {state.retry_count + 1})")
    print(f"  State received by generate_story: ")
    print(f"    Question: '{state.question}'")
    print(f"    Answer: '{state.answer}'")


    story_response = await story_chain.ainvoke(prompt_input)

    print(f"  Generated story (first 100 chars): {story_response.content[:100]}...")

    # Clear instructions after using them for this generation
    return {"story": story_response.content, "story_generation_instructions": ""}


# check_story_scary remains the same
async def check_story_scary(state: State, config: RunnableConfig) -> Dict[str, Any]:
    """
    Uses an LLM to evaluate if the generated story is scary and meets the criteria.
    Returns reason for scary or not scary.
    """
    model_name = config.get('configurable', {}).get('model_name', "claude-3-haiku-20240307")
    llm = ChatAnthropic(model_name=model_name, temperature=0.0)

    scary_prompt_template = ChatPromptTemplate.from_messages(
        [
            SystemMessage(
                "You are an AI assistant tasked with evaluating stories for their scare factor. "
                "Your goal is to determine if a given story is genuinely scary, creepy, or unsettling. "
                "It should evoke a sense of dread, fear, or suspense. "
                "Respond with ONLY 'SCARY' if it meets this criterion, otherwise respond with ONLY 'NOT_SCARY'. "
                "Do NOT add any other text or explanation. Your output must be one word."
                "Consider these points:\n"
                "- Does it create a chilling atmosphere?\n"
                "- Is there genuine suspense or horror?\n"
                "- Does it evoke fear or discomfort?\n"
                "- Is the provided question and answer integrated into the scary narrative?\n"
            ),
            HumanMessage(
                f"Here is the question: '{state.question}'\n"
                f"Here is the answer: '{state.answer}'\n"
                f"Here is the story: '{state.story}'\n\n"
                "Is this story scary? Respond with 'SCARY:' or 'NOT_SCARY:'."
            )
        ]
    )

    scary_chain = scary_prompt_template | llm

    print(f"\n--- Node: check_scary_node --- (Attempt {state.retry_count + 1})")
    print(f"  Checking scariness of story (first 50 chars): '{state.story[:50]}...'")
    evaluation_response = await scary_chain.ainvoke({
        "question": state.question,
        "answer": state.answer,
        "story": state.story
    })

    feedback = evaluation_response.content.strip().upper()
    
    print(f"  Story scariness feedback: {feedback}")

    return {"scary_check_feedback": feedback}


# check_story_funny remains the same
async def check_story_funny(state: State, config: RunnableConfig) -> Dict[str, Any]:
    """
    Uses an LLM to evaluate if the generated story is funny.
    Returns reason for funny or not funny.
    """
    model_name = config.get('configurable', {}).get('model_name', "claude-3-haiku-20240307")
    llm = ChatAnthropic(model_name=model_name, temperature=0.0) # Low temp for strict evaluation

    funny_prompt_template = ChatPromptTemplate.from_messages(
        [
            SystemMessage(
                "You are an AI comedian evaluator. Your task is to determine if the given story "
                "is genuinely funny, amusing, or has a clear comedic element, especially considering "
                "how the question and answer were used. "
                "Respond with ONLY 'FUNNY' if it achieves this, otherwise respond with ONLY 'NOT_FUNNY'. "
                "Do NOT add any other text or explanation. Your output must be one word."
            ),
            HumanMessage(
                f"Question: '{state.question}'\n"
                f"Answer: '{state.answer}'\n"
                f"Story: '{state.story}'\n\n"
                "Is this story funny? Respond 'FUNNY:' or 'NOT_FUNNY:' and then explicitly why you think so."
            )
        ]
    )

    funny_chain = funny_prompt_template | llm

    print(f"\n--- Node: check_story_funny --- (Attempt {state.retry_count + 1})")
    print(f"  Checking funniness of story (first 50 chars): '{state.story[:50]}...'")
    evaluation_response = await funny_chain.ainvoke({
        "question": state.question,
        "answer": state.answer,
        "story": state.story
    })

    feedback = evaluation_response.content.strip().upper()
    print(f"  Story funniness feedback: {feedback}")

    return {"funniness_check_feedback": feedback}


async def create_story_instructions(state: State, config: RunnableConfig) -> Dict[str, Any]:
    """
    Generates specific instructions for the story node based on feedback.
    Increments retry count and sets termination flag if max retries hit.
    """
    current_retry_count = state.retry_count + 1
    max_retries = state.max_retries
    model_name = config.get('configurable', {}).get('model_name', "claude-3-haiku-20240307")
    llm = ChatAnthropic(model_name=model_name, temperature=0.5)

    print(f"\n--- Node: create_story_instructions --- (Attempt {current_retry_count}/{max_retries})")

    if current_retry_count > max_retries:
        print(f"  Max retries ({max_retries}) reached for story generation. Setting terminate_flag to True.")
        return {
            "retry_count": current_retry_count,
            "story": "",
            "scary_check_feedback": "",
            "funniness_check_feedback": "",
            "story_generation_instructions": "",
            "terminate_flag": True
        }

    instructions = ""
    scary_reason = ""
    # Check if the specific scary feedback indicates it was NOT_SCARY
    if state.scary_check_feedback.startswith("NOT_SCARY"):
        # Extract the reason/suggestions from the feedback
        scary_reason = state.scary_check_feedback.replace("NOT_SCARY", "").strip() + "This needs to be more scary."
        print(f"  Story not scary. Generating instructions based on feedback: '{scary_reason}'")
        # Prompt the LLM to convert the reason into actionable instructions
        prompt_template = ChatPromptTemplate.from_messages(
            [
                SystemMessage(
                    "You are an AI assistant tasked with converting feedback into concise, actionable instructions "
                    "for a storyteller. The feedback will explain why a story wasn't scary and provide suggestions. "
                    "Your goal is to guide the storyteller to revise the story to make it more SCARY, creepy, or suspenseful, "
                    "without changing the core question and answer. "
                    "Provide only the instructions, starting directly with the instruction phrase. "
                    "Keep them focused and brief, no more than 1-2 sentences. "
                    "Incorporate the provided feedback directly into the instruction."
                ),
                HumanMessage(
                    f"The original question was: '{state.question}'\n"
                    f"The answer was: '{state.answer}'\n"
                    f"The current story: '{state.story}'\n"
                    f"Feedback: '{scary_reason}'\n\n"
                    "Generate instructions to make this story scary, incorporating the feedback."
                )
            ]
        )
        instruction_chain = prompt_template | llm
        instruction_response = await instruction_chain.ainvoke({
            "question": state.question,
            "answer": state.answer,
            "story": state.story,
            "scary_reason": scary_reason
        })
        instructions = instruction_response.content.strip()

    elif state.funniness_check_feedback == "NOT_FUNNY":
        print("  Story not funny. Generating instructions to make it funny.")
        # Your existing funny instruction generation logic here
        prompt_template = ChatPromptTemplate.from_messages(
            [
                SystemMessage(
                    "You are an AI assistant tasked with providing concise, actionable instructions "
                    "to a storyteller. Your goal is to guide the storyteller to revise a story "
                    "to make it more FUNNY, amusing, or comedic, without changing the core question and answer. "
                    "Provide only the instructions, starting directly with the instruction phrase. "
                    "Keep them focused and brief, no more than 1-2 sentences."
                ),
                HumanMessage(
                    f"The original question was: '{state.question}'\n"
                    f"The answer was: '{state.answer}'\n"
                    f"The current story (which was not funny): '{state.story}'\n\n"
                    "Generate instructions to make this story funny, focusing on comedic timing, absurd situations, or witty dialogue."
                )
            ]
        )
        instruction_chain = prompt_template | llm
        instruction_response = await instruction_chain.ainvoke({
            "question": state.question,
            "answer": state.answer,
            "story": state.story
        })
        instructions = instruction_response.content.strip()
    else:
        print("  Neither specific scary nor funny feedback indicating NOT_SCARY/NOT_FUNNY. Generating generic refinement instructions.")
        instructions = "Revise the story to improve its overall quality and integration of the question/answer."


    print(f"  Generated instructions: '{instructions}'")

    return {
        "retry_count": current_retry_count,
        "story": state.story,
        "scary_check_feedback": scary_reason,
        "funniness_check_feedback": "",
        "story_generation_instructions": instructions,
        "terminate_flag": False
    }

# Router after scary check (unchanged logic)
def route_after_scary_check(state: State) -> Literal["check_funniness", "create_story_instructions"]:
    """
    Decides the next step based on the story's scariness feedback.
    If SCARY, proceed to funniness check. If NOT_SCARY, go create instructions.
    """
    print(f"\n--- Router: route_after_scary_check --- (Current retry: {state.retry_count}/{state.max_retries})")
    print(f"  Scary feedback: {state.scary_check_feedback}")

    if state.scary_check_feedback == "SCARY":
        print("  Story is SCARY. Proceeding to funniness check.")
        return "check_funniness"
    else: # If NOT_SCARY or any unexpected feedback, always try to regenerate story with instructions
        print("  Story is NOT_SCARY or feedback is unexpected. Generating instructions for story revision.")
        return "create_story_instructions"


# Router after creating instructions (decides whether to loop or end)
def route_after_create_instructions(state: State) -> Literal["generate_story", "__end__"]:
    """
    Decides whether to continue regenerating the story or end if max retries reached,
    based on the terminate_flag set by create_story_instructions.
    """
    print(f"\n--- Router: route_after_create_instructions --- (Current retry: {state.retry_count}/{state.max_retries})")
    if state.terminate_flag:
        print(f"  Terminate flag set by create_story_instructions. Ending graph.")
        return "__end__"
    else:
        print("  Max retries not reached. Re-generating story with new instructions.")
        return "generate_story" # Loop back to generate story


# Router after funniness check (unchanged logic)
def route_after_funniness_check(state: State) -> Literal["create_story_instructions", "__end__"]:
    """
    Decides the next step based on the story's funniness feedback.
    If funny, end. If not funny, go create instructions.
    """
    print(f"\n--- Router: route_after_funniness_check --- (Current retry: {state.retry_count}/{state.max_retries})")
    print(f"  Funniness feedback: {state.funniness_check_feedback}")

    if state.funniness_check_feedback == "FUNNY":
        print("  Story is FUNNY. Ending graph.")
        return "__end__"
    else: # If NOT_FUNNY or any unexpected feedback, always try to regenerate story with instructions
        print("  Story is NOT_FUNNY or feedback is unexpected. Generating instructions for story revision.")
        return "create_story_instructions"


# Define the graph
graph = (
    StateGraph(State, config_schema=Configuration)
    .add_node("claude_node", call_claude)
    .add_node("story_node", generate_story)
    .add_node("check_scary_node", check_story_scary)
    .add_node("check_story_funny_node", check_story_funny)
    .add_node("create_story_instructions_node", create_story_instructions) # Renamed/Modified node

    .add_edge("__start__", "claude_node")
    .add_edge("claude_node", "story_node")
    .add_edge("story_node", "check_scary_node")

    # Conditional edge after scary check
    .add_conditional_edges(
        "check_scary_node",
        route_after_scary_check,
        {
            "check_funniness": "check_story_funny_node", # If SCARY, go to funniness check
            "create_story_instructions": "create_story_instructions_node", # If NOT_SCARY, go to generate instructions
        }
    )

    # Conditional edge after creating instructions (decides whether to loop or end)
    .add_conditional_edges(
        "create_story_instructions_node",
        route_after_create_instructions, # New router name
        {
            "generate_story": "story_node", # Loop back to generate story with instructions
            "__end__": END # If max retries reached
        }
    )

    # Conditional edge after funniness check
    .add_conditional_edges(
        "check_story_funny_node",
        route_after_funniness_check,
        {
            "create_story_instructions": "create_story_instructions_node", # If NOT_FUNNY, go to generate instructions
            "__end__": END # If FUNNY
        }
    )

    .compile(name="Question Answer Story with Feedback-Driven Retries")
)

async def run_example():
    if not os.getenv("ANTHROPIC_API_KEY"):
        raise ValueError("ANTHROPIC_API_KEY environment variable not set.")

    # A challenging question that might need iterative refinement
    initial_question = "Why did the sentient shadow always carry a rubber duck into the haunted attic?"

    initial_state = State(question=initial_question)

    config_for_run = {
        "configurable": {
            "temperature": 0.5,
            "model_name": "claude-3-haiku-20240307",
            "story_temperature": 0.9,
            "max_retries": 5 # Set overall max_retries for story generation attempts
        }
    }

    initial_state.max_retries = config_for_run["configurable"].get("max_retries", initial_state.max_retries)


    print(f"\n--- Starting Graph Run ---")
    print(f"Initial state: {initial_state}")
    print(f"Running graph with config: {config_for_run}")

    print("\n--- Streaming Graph Output ---")
    async for chunk in graph.astream(initial_state, config=config_for_run):
        print(chunk)
    print("--- End Streaming ---")

    final_state = await graph.ainvoke(initial_state, config=config_for_run)
    print(f"\n--- Final Graph State ---")
    print(f"\nFinal State (after full run): {final_state}")
    print(f"\nClaude's Answer: {final_state.answer}")
    print(f"\nGenerated Story:\n{final_state.story}")
    print(f"\nStory Scary Check Feedback: {final_state.scary_check_feedback}")
    print(f"\nStory Funniness Check Feedback: {final_state.funniness_check_feedback}")
    print(f"Total Retries: {final_state.retry_count}")
    if final_state.story_generation_instructions:
        print(f"\nRemaining Story Generation Instructions (if any): {final_state.story_generation_instructions}")


if __name__ == "__main__":
    import asyncio
    asyncio.run(run_example())