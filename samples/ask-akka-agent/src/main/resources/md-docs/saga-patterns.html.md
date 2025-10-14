<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Saga patterns](saga-patterns.html)

<!-- </nav> -->

# Saga patterns

Saga patterns help manage long-running business processes in distributed systems by dividing them into a series of transactions. Each transaction either completes successfully or triggers compensating actions if something goes wrong.

There are two common approaches to implementing Saga patterns: **choreography-based** and **orchestrator-based**. Both approaches ensure system consistency. They differ in how control and coordination are handled.

## <a href="about:blank#_overview"></a> Overview

| Orchestrator Pattern | Choreography Pattern |
| --- | --- |
| A central controller, or orchestrator, coordinates the process. It manages the sequence of steps to ensure that each transaction completes or that compensating actions are taken on failure. | Each service listens for events and acts independently. When it completes a transaction, it emits an event that triggers the next service. If a failure occurs, the service handles rollback logic. |
| - Centralized control and logic
  - Easier to track progress and transaction state
  - Clear audit trail
  - Can become a coordination bottleneck
  - Tighter coupling between orchestrator and services | - Decentralized control
  - Low coordination overhead
  - Services are only coupled to events
  - Increased complexity in ensuring proper failure handling
  - Harder to debug and monitor long-running flows |
| In Akka, you can implement this pattern using the [Workflow](../java/workflows.html) component. The Workflow defines each step and manages retries, timeouts, and compensating actions. | In Akka, you can implement this pattern by combining components such as [Entities](../java/event-sourced-entities.html) and [Consumers](../java/consuming-producing.html), each producing and reacting to events. |
| Example: [Funds Transfer Workflow Between Two Wallets](https://github.com/akka-samples/transfer-workflow-orchestration) | Example: [User Registration Service](https://github.com/akka-samples/choreography-saga-quickstart) |

## <a href="about:blank#_choosing_the_right_pattern"></a> Choosing the right pattern

When selecting a Saga pattern, consider the architecture of your system and the nature of the business process.

Use choreography-based Sagas if:

- Your services are autonomous and can handle failure independently
- You prefer low coupling and high scalability
- You are comfortable with distributed control and eventual consistency
- You do not require central tracking of each step
- You are confident the complexity will be manageable as the system grows
Use orchestrator-based Sagas if:

- Your process includes tightly coordinated steps
- You need centralized visibility and clear state tracking
- Retrying, error handling, and compensation must be handled consistently
- You are fine with introducing a central coordination point
- You want assurance that complexity will scale more predictably as the system grows

## <a href="about:blank#_how_to_decide"></a> How to decide

- If your services benefit from independent execution and localized failure logic, choreography is a good fit. Be mindful that as more components participate in the flow, managing event-driven coordination and compensating logic can become more difficult.
- If your process requires clear visibility into progress and easier failure recovery, an orchestrator may be more suitable. Centralized coordination helps keep complexity manageable as the system evolves.

## <a href="about:blank#_flexibility"></a> Flexibility

It is possible to use both patterns in the same application. An orchestrator may manage the main business flow while individual services apply choreography to manage local side effects or edge cases. This combination allows a balanced trade-off between control and autonomy.

<!-- <footer> -->
<!-- <nav> -->
[Multi-region operations](multi-region.html) [Endpoints](grpc-vs-http-endpoints.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->