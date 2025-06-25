<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [AI Agents](ai-agents.html)

<!-- </nav> -->

# AI Agents

AI agents are components that integrate with AI to perceive their environment, make decisions, and take actions toward a specific goal. Agents can have varying degrees of human intervention from none (completely autonomous) to requiring a human to approve each action the agent takes.

## <a href="about:blank#_tokens_and_streaming"></a> Tokens and streaming

Agents interact with AI, most commonly in the form of Large Language Models (LLMs). LLMs are what is known as *predictive text*. This means that every word streamed to the agent is actually just the next word predicted to be in the output. Regardless of platform or language, agents need the ability to stream tokens bi-directionally.

If your agent is consuming an LLM as a service, then you could be paying some amount of money per bundle of tokens. In cases like this, it is crucial to ensure that you have control over how frequently and how many tokens the agent "spends."

## <a href="about:blank#_different_types_of_ai"></a> Different types of AI

LLMs are everywhere these days and it is impossible to escape all of their related news. It would be easy to assume that all agents interact with LLMs whether they are self-hosted or provided as an external service. This idea does a disservice to the rest of machine learning and AI in particular.

As you develop your teams of collaborative agents, keep in mind that not everything needs to be an LLM and look for opportunities to use smaller, more efficient, task-specific models. This can not only save you money, but can improve the overall performance of your application.

## <a href="about:blank#_prompts_session_memory_and_context"></a> Prompts, session memory, and context

Agents interact with LLMs through prompts. A prompt is the input to an LLM in the form of natural language text. The quality and detail of your agents' prompts can make the difference between a great application experience and a terrible one. The prompt sent to an LLM typically tells the model the role it is supposed to play, how it should respond (e.g. you can tell a model to respond with JSON).

Take a look at the following sample prompt:

```none
You are a friendly and cheerful question answerer.
Answer the question based on the context below.
Keep the answer short and concise. Respond "Unsure about answer"
if not sure about the answer.

If asked for a single item and multiple pieces of information
are acceptable answers, choose one at random.

Context:
 Here is a summary of all the action movies you know of. Each one is rated from 1 to 5 stars.

Question:
  What is the most highly rated action movie?
```
Everything except the **question** above would have been supplied by the agent. Working with and honing prompts is such an important activity in agentic development that a whole new discipline called [prompt engineering](https://www.promptingguide.ai/) has sprung up around it.

The context in the preceding prompt is how agents can augment the knowledge of an LLM. This is how Retrieval Augmented Generation (RAG) works. Agents can participate in sessions where the conversation history is stored. You see this in action whenever you use an AI assistant and it shows you a history of all of your chats. Session management and persistence is a task every agent developer needs to tackle.

## <a href="about:blank#_agent_orchestration_and_collaboration"></a> Agent orchestration and collaboration

Each agent should do *one thing*. Agents should have a single goal and they can use any form of knowledge and model inference to accomplish that goal. However, we rarely ever build applications that only do one thing. One of the super powers of agents is in *collaboration*.

There are protocols and standards rapidly evolving for ways agents can communicate with each other directly, but agents also benefit from indirect communication.

Whether you have 1 agent or 50, you still need to be able to do things like recover from network failure, handle timeouts, deal with failure responses, broken streams, and much more. Just for individual agents you will need an orchestrator if you want that agent to be resilient at scale. With dozens of agents working together with shared and isolated sessions, they will need to be managed by supervisors.

## <a href="about:blank#_agent_evaluation"></a> Agent evaluation

The answers your agents get from models are *non-deterministic*. They can seem random at times. Since you can’t predict the model output you can’t use traditional testing practices. Instead, we need to do what’s called **evaluation**. Evaluation involves iteratively refining a prompt.

You submit the prompt to the model and get an answer back. Then, we can use *another model* to derive metrics from that response like confidence ratings. This is often called the "LLM-as-judge" pattern.

Rather than a unit test, we often have entire suites of evaluation runs where we submit a large number of prompts, derive analytics and metrics from the replies, and then score the model-generated data as a whole. This is tricky because you can very easily have an evaluation run that has a high confidence score but still somehow manages to contain [hallucinations](https://www.ibm.com/think/topics/ai-hallucinations).

<!-- <footer> -->
<!-- <nav> -->
[Foundational AI concepts (video)](ai-concepts-video.html) [Access Control List concepts](acls.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->