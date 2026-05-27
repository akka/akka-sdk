<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [Streaming AI](streaming-ai.html)

<!-- </nav> -->

# Streaming AI

Process real-time data streams with AI-powered feedback loops, event-driven consumers, and streaming agent responses. This pattern covers consuming events from external systems, applying AI analysis in real time, and producing enriched outputs or triggering downstream actions based on streaming data.

## <a href="about:blank#_overview"></a> Overview

### <a href="about:blank#_when_to_use_this_pattern"></a> When to Use This Pattern

- You need to process a continuous stream of events with AI-powered analysis
- Your application requires real-time feedback loops where AI responses trigger further actions
- You want to consume events from message brokers and enrich them with LLM processing
- You need streaming token-by-token agent responses for responsive user experiences
- You want to stream LLM responses through an orchestration Workflow and back out to an Endpoint

### <a href="about:blank#_akka_components_involved"></a> Akka Components Involved

- **Consumers** — subscribe to event streams from Entities or external message brokers
- **Agents** — process streaming data with LLM-powered analysis and generate streaming responses
- **Workflows** — orchestrate streaming pipelines that route LLM responses through multi-step processing
- **Event Sourced Entities** — produce durable event streams that Consumers subscribe to
- **HTTP Endpoints** — expose streaming responses to clients via WebSocket or server-sent events

## <a href="about:blank#_sample_projects"></a> Sample Projects

The following sample projects demonstrate this pattern:

- [iot-sensor-monitoring](https://github.com/akka-samples/temperature-monitoring-agent) — real-time AI analysis of IoT sensor data streams
- [event-sourced-customer-registry-subscriber](https://github.com/akka-samples/event-sourced-customer-registry) — event-driven consumer that reacts to entity state changes

## <a href="about:blank#_see_also"></a> See Also

- [Consuming and producing](../consuming-producing.html)
- [Streaming responses](../agents/streaming.html)
- [Implementing Workflows](../workflows.html)
- [Designing HTTP Endpoints](../http-endpoints.html)
- [Akka Streaming: High-performance stream processing](https://akka.io/blog/akka-streaming-high-performance-stream-processing-for-real-time-ai)
- [Building a real-time video AI service with Google Gemini](https://akka.io/blog/building-real-time-video-ai-service-with-google-gemini)

<!-- <footer> -->
<!-- <nav> -->
[RAG & knowledge](rag-and-knowledge.html) [Orchestration & durability](orchestration-and-durability.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->