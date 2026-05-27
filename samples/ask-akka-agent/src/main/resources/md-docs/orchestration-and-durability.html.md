<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [Orchestration & durability](orchestration-and-durability.html)

<!-- </nav> -->

# Orchestration & durability

Build long-running, multi-step business processes that survive failures and recover gracefully. This pattern covers durable workflow orchestration with compensation logic, saga patterns for distributed transactions, and reliable timers for scheduled operations — all with built-in fault tolerance.

## <a href="about:blank#_overview"></a> Overview

### <a href="about:blank#_when_to_use_this_pattern"></a> When to Use This Pattern

- You need multi-step business processes that must complete reliably despite failures
- Your application requires compensation logic to undo partial work when a step fails
- You want to implement saga patterns for coordinating distributed transactions
- You need reliable timers or scheduled actions that survive process restarts

### <a href="about:blank#_akka_components_involved"></a> Akka Components Involved

- **Workflows** — define multi-step processes with compensation, retries, and durable state
- **Timed Actions** — schedule reliable timers and periodic operations
- **Entities** — maintain durable state for each step in the orchestration

## <a href="about:blank#_sample_projects"></a> Sample Projects

The following sample projects demonstrate this pattern:

- [transfer-workflow](https://github.com/akka-samples/transfer-workflow) — basic multi-step transfer workflow
- [transfer-workflow-orchestration](https://github.com/akka-samples/transfer-workflow-orchestration) — orchestration-based saga for fund transfers
- [transfer-workflow-compensation](https://github.com/akka-samples/transfer-workflow-compensation) — transfer workflow with compensation logic for failure recovery
- [choreography-saga-quickstart](https://github.com/akka-samples/choreography-saga-quickstart) — choreography-based saga pattern for distributed coordination

## <a href="about:blank#_see_also"></a> See Also

- [Implementing Workflows](../workflows.html)
- [Timers](../timed-actions.html)
- [Saga patterns](../../concepts/saga-patterns.html)

<!-- <footer> -->
<!-- <nav> -->
[Streaming AI](streaming-ai.html) [APIs & exposure](apis-and-exposure.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->