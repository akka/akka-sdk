<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [Memory & state](memory-and-state.html)

<!-- </nav> -->

# Memory & state

Persist and manage durable state with sub-10ms access latency using event sourcing and key-value patterns. This use case covers how to model stateful entities, maintain conversation memory, build queryable views over your data, and choose the right persistence pattern for your application’s needs.

## <a href="about:blank#_overview"></a> Overview

### <a href="about:blank#_when_to_use_this_pattern"></a> When to Use This Pattern

- You need durable, low-latency state that survives restarts and scales horizontally
- Your application requires event sourcing for full audit trails and temporal queries
- You want simple key-value storage for session memory, caches, or configuration
- You need to build read-optimized views that aggregate data across multiple entities

### <a href="about:blank#_akka_components_involved"></a> Akka Components Involved

- **Event Sourced Entities** — persist state as an immutable sequence of events with full history
- **Key Value Entities** — store and retrieve state directly with simple get/set semantics
- **Views** — build read-optimized projections over entity state for queries and reporting

## <a href="about:blank#_sample_projects"></a> Sample Projects

The following sample projects demonstrate this pattern:

- [travel-planning-agent](https://github.com/akka-samples/travel-agent) — session memory for multi-turn travel planning conversations
- [shopping-cart-quickstart](https://github.com/akka-samples/shopping-cart-quickstart) — event sourced shopping cart with views
- [event-sourced-customer-registry](https://github.com/akka-samples/event-sourced-customer-registry) — event sourced entity pattern for customer data

## <a href="about:blank#_see_also"></a> See Also

- [Implementing Event Sourced Entities](../event-sourced-entities.html)
- [Implementing key value entities](../key-value-entities.html)
- [Implementing Views](../views.html)

<!-- <footer> -->
<!-- <nav> -->
[Multi-agent systems](multi-agent-systems.html) [RAG & knowledge](rag-and-knowledge.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->