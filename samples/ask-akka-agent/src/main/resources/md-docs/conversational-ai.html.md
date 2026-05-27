<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [Conversational AI](conversational-ai.html)

<!-- </nav> -->

# Conversational AI

Build conversational AI agents that maintain session memory across multi-turn interactions, stream responses in real time, and handle complex dialogue flows. This pattern covers stateful chat agents that remember context, adapt to user intent, and integrate with external tools and knowledge sources.

## <a href="about:blank#_overview"></a> Overview

### <a href="about:blank#_when_to_use_this_pattern"></a> When to Use This Pattern

- You need a chat-based agent that remembers prior turns in a conversation
- Your application requires streaming token-by-token responses to end users
- You want to build a customer-facing assistant with tool use and contextual memory
- You need multi-turn interactions where each turn builds on previous context

### <a href="about:blank#_akka_components_involved"></a> Akka Components Involved

- **Agents** — core conversational logic and LLM integration
- **HTTP Endpoints** — expose the agent via REST or WebSocket for client applications
- **Session Memory (Key Value Entities)** — persist conversation history and session state with sub-10ms access

## <a href="about:blank#_sample_projects"></a> Sample Projects

The following sample projects demonstrate this pattern:

- [helloworld-agent](https://github.com/akka-samples/helloworld-agent) — minimal agent showing basic conversational interaction
- [travel-planning-agent](https://github.com/akka-samples/travel-agent) — travel planning assistant with session state and external integrations

## <a href="about:blank#_see_also"></a> See Also

- [Agents](../agents.html)
- [Managing session memory](../agents/memory.html)
- [Streaming responses](../agents/streaming.html)

<!-- <footer> -->
<!-- <nav> -->
[Use cases](index.html) [Autonomous agents](autonomous-agents.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->