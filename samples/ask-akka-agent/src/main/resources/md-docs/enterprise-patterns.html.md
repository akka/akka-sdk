<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [Enterprise patterns](enterprise-patterns.html)

<!-- </nav> -->

# Enterprise patterns

Implement classic enterprise patterns with Akka including shopping carts, customer registries, saga orchestration, and Spring framework integration. These patterns demonstrate how Akka’s entity model, workflow engine, and view system handle traditional enterprise use cases with built-in durability, scalability, and low-latency access.

## <a href="about:blank#_overview"></a> Overview

### <a href="about:blank#_when_to_use_this_pattern"></a> When to Use This Pattern

- You are building e-commerce systems with shopping carts, inventories, or order management
- Your application needs customer or entity registries with CRUD operations and search
- You want to implement saga patterns for coordinating multi-service business transactions
- You need to integrate Akka components with an existing Spring-based application

### <a href="about:blank#_akka_components_involved"></a> Akka Components Involved

- **Event Sourced Entities** — model domain objects with full event history and temporal queries
- **Key Value Entities** — simple stateful entities for registries and lookups
- **Workflows** — orchestrate multi-step business processes and saga patterns
- **Views** — build queryable projections over entity state for search and reporting

## <a href="about:blank#_sample_projects"></a> Sample Projects

The following sample projects demonstrate this pattern:

- [shopping-cart-quickstart](https://github.com/akka-samples/shopping-cart-quickstart) — event sourced shopping cart with views and projections
- [choreography-saga-quickstart](https://github.com/akka-samples/choreography-saga-quickstart) — choreography-based saga for distributed business transactions

## <a href="about:blank#_see_also"></a> See Also

- [Implementing Event Sourced Entities](../event-sourced-entities.html)
- [Implementing Workflows](../workflows.html)
- [Saga patterns](../../concepts/saga-patterns.html)

<!-- <footer> -->
<!-- <nav> -->
[Governance & compliance](governance-and-compliance.html) [Integrations](../integrations/index.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->