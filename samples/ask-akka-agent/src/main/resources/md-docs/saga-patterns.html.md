<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Saga patterns](saga-patterns.html)

<!-- </nav> -->

# Saga patterns

Saga patterns manage long-running business processes in distributed systems by dividing them into series of transactions. Each transaction either completes successfully or triggers compensating actions if something goes wrong. There are two main approaches to implementing Sagas: **choreography-based** and **orchestrator-based**. Both patterns ensure system consistency, but they differ in how coordination and control are handled.

## <a href="about:blank#_choreography_based_saga_pattern"></a> Choreography-Based Saga pattern

In a choreography-based Saga, each service involved in the process listens for events and independently performs its actions. When a service completes a transaction, it emits an event that triggers the next service in the sequence. If a service encounters a failure, it is responsible for triggering compensating actions to undo the previous steps.

Choreography provides a decentralized approach, with no central authority managing the process. This makes it well-suited for systems where loose coupling and scalability are key, as each service handles its own part of the transaction.

**Advantages:**

- Decentralized control and lower coordination overhead.
- Services operate independently, reacting to events.
**Challenges:**

- Increased complexity in ensuring all services handle failures correctly.
- Harder to track and debug long-running processes without a central point of control.
You can build choreography-based Sagas by combining Akka components such as Entities, Consumers, and Timers. Check out the Choreography Saga sample that can be downloaded as a [zip file](../java/_attachments/choreography-saga-quickstart.zip).

## <a href="about:blank#_orchestrator_based_saga_pattern"></a> Orchestrator-Based Saga pattern

In an orchestrator-based Saga, a central controller (or orchestrator) coordinates the entire process. This orchestrator manages the sequence of steps, ensuring that each transaction completes successfully or triggers compensating actions if a failure occurs.

In Akka, *Workflows* implement the orchestrator-based approach. The Workflow defines each step of the process, invoking different Akka components or services. If a step fails, the orchestrator handles retries, timeouts, or compensating actions, maintaining overall control of the process.

**Advantages:**

- Centralized control simplifies managing the flow and handling failures.
- Easier to track the progress and state of long-running processes.
**Challenges:**

- Single point of coordination may become a bottleneck.
- Increased coupling between the orchestrator and individual services.
For more information on implementing Saga patterns with Workflows, refer to the [Workflow documentation](../java/workflows.html).

## <a href="about:blank#_choosing_the_right_saga_pattern"></a> Choosing the right saga pattern

When deciding between choreography-based and orchestrator-based Saga patterns, consider the following:

- **Choreography-Based Sagas** are ideal when you need loose coupling between services, high scalability, and decentralized control. This approach allows each service to react independently to events without relying on a central coordinator. It’s a good fit for systems where services need to be autonomous, and failure handling can be distributed. However, debugging and monitoring can be more challenging as there is no central point of control.
- **Orchestrator-Based Sagas** are better suited when you require centralized control over the flow of the process. If the business process involves tightly coordinated steps, an orchestrator provides better visibility and control, making it easier to handle retries, failures, and compensating actions. This approach is also useful if your system needs to track the state of a long-running process or ensure consistency across multiple steps. However, it introduces more coupling between services and can become a bottleneck.
**How to Choose**:

- If your system benefits from **autonomous services** that can scale independently and handle their own failure logic, consider **choreography-based Sagas**.
- If your process requires **centralized tracking**, **clear visibility** into the state of transactions, and **easier failure management**, use an **orchestrator-based Saga** like Workflows.
While both the choreography-based and orchestrator-based Saga patterns have their unique strengths, it’s important to recognize that they aren’t mutually exclusive. In fact, depending on the use case, both patterns can be combined within the same application to handle different parts of a process. For instance, an orchestrator-based Saga might be used for the main flow, while an choreography-based approach can manage more complex edge cases or failure scenarios, providing flexibility and robustness in handling long-running business processes.

<!-- <footer> -->
<!-- <nav> -->
[Multi-region operations](multi-region.html) [Endpoints](grpc-vs-http-endpoints.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->