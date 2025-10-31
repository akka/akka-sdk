package com.example.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.testkit.TestKitSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class CompactionAgentIntegrationTest extends TestKitSupport {

  @Test
  public void testCompaction() {
    var sessionId = UUID.randomUUID().toString();

    var userMessages = new ArrayList<String>();
    var aiMessages = new ArrayList<String>();

    userMessages.add("What are the core components of Akka?");
    aiMessages.add(
      """
      Core Components of Akka
      Akka consists of several key components that are essential for building responsive applications. Here’s a brief overview of these core components:

      1. Entities
      Entities act as in-memory databases, managing persistent state and allowing developers to focus on business logic. They can be either event-sourced or key-value based.

      2. Endpoints
      Endpoints expose your services to the outside world, enabling external access to your application.

      3. Timed Actions
      These are scheduled executions within the application, ensuring reliable execution that is guaranteed to run at least once.

      4. Views
      Views implement the Command Query Responsibility Segregation (CQRS) pattern, allowing for streaming projections that separate read and write operations across services.

      5. Workflows
      Workflows support durable, long-running processes orchestrated through Saga patterns, managing complex business logic and state changes over time.

      These components work together within the Akka platform to allow developers to create scalable, resilient, and responsive applications efficiently! 🎉
      """.stripIndent()
    );

    userMessages.add("Explain event sourced entity in more detail");
    aiMessages.add(
      """
      Event Sourced Entities
      Overview
      Event Sourced Entities are a fundamental part of the Akka framework, utilizing the Event Sourcing Model to maintain their state. Instead of storing the current state, these entities capture and persist all events that led to the current state in a journal. This model ensures reliable data handling through ACID semantics, facilitates horizontal scaling, and provides isolation from failures.

      Key Concepts
      Journal: Akka uses a journal to persistently store events from Event Sourced Entities. This process is managed automatically by Akka, relieving developers from the burden of configuring and managing data storage.
      Events: Any command that requires changing the state must result in the persistence of an event. This event is then processed to change the in-memory state of the entity.
      Snapshots: To optimize loading, Event Sourced Entities periodically save snapshots of their state, allowing the system to restore state more efficiently by starting from the most recent snapshot and applying only the necessary events afterward.
      Command Processing
      When handling a command, the flow includes:

      A client sends a request to an endpoint.
      The endpoint sends a command to the corresponding Event Sourced Entity.
      The entity processes the command and emits events that describe the changes.
      After successfully storing the events in the journal, the entity updates its own state based on those events.
      State Coordination and Multi-Region Support
      Event Sourced Entities are designed to operate across multiple regions, enabling a read-anywhere model while ensuring that only the origin region can modify the state. Writes are routed appropriately to this origin region, making the system resilient to network partitions.

      Replication
      These entities support automatic replication of state across all regions in an Akka project. However, if you need multi-writer capabilities, you will have to implement a Conflict-Free Replicated Data Type (CRDT) to allow concurrent and independent updates across different entity instances.

      Related Concepts
      Effect API: Each operation must return an Effect, which can manage the infrastructure-related functionality transparently.
      Event Handler: This is the specific piece of code responsible for updating the Event Sourced Entity's state based on received events.
      Example
      Consider a shopping cart example:

      Events such as ItemAdded, ItemRemoved, and CheckedOut are used to capture the state changes of the cart.
      For instance, adding 3 Akka T-shirts would emit an ItemAdded event that is then stored in the journal, subsequently updating the internal state of the shopping cart entity.
      Conclusion
      Event Sourced Entities in Akka provide a powerful mechanism for managing state in a way that exploits the benefits of event sourcing, ensuring persistence, scalability, and fault recovery. By modeling state changes as events, these entities promote a clear separation between state and business logic, enhancing maintainability and scalability.
      """.stripIndent()
    );

    userMessages.add("How is key value entity different?");
    aiMessages.add(
      """
      Key Value Entities vs. Event Sourced Entities
      Key Value Entities
      Persistence: Key Value Entities persist the full state of the object on every change. Only the latest state is stored, meaning the history of changes is not accessible.
      Locking: They behave similarly to database records, writing and effectively locking the whole row during updates.
      Event Subscription: Although they use an underlying event-based architecture, they do not express state changes in events like Event Sourced Entities.
      Replication: Currently, Key Value Entities do not replicate their state across regions; the data exists only in the primary region, with all requests from other regions being forwarded there.
      Data Model: They work with a simple key/value store model, capturing the entire state in a single unit.
      Event Sourced Entities
      Persistence: Event Sourced Entities persist events instead of state. The current state is derived from these events held in the event journal.
      State Changes: They express state changes as events that can be applied to update the state.
      Replication: Event Sourced Entities automatically replicate their state across regions by default, allowing multi-reader capabilities.
      Views and Consumers: Readers can access the event journal independently to create Views or perform business actions via Consumers.
      Summary
      In summary, the main differences lie in how data is persisted (full state vs. event-based), history accessibility, replication capabilities, and the way they handle state updates. Key Value Entities are straightforward and efficient for scenarios where the latest state is all that's needed, while Event Sourced Entities offer rich history and flexibility at the cost of complexity.
      """.stripIndent()
    );

    userMessages.add("When shall I use even sourced vs key value?");
    aiMessages.add(
      """
          When to Use Event Sourced vs Key Value
          Event Sourced Entities
          State Changes:
          Event Sourced Entities express state changes as events that are stored in a journal.
          They allow for the current state of an entity to be derived from these events.
          Use Cases:
          Ideal when you need to track the history of changes to the state over time.
          Suitable for scenarios where you want to create views or perform business actions based on historical events.
          Key Value Entities
          State Changes:
          Only the current state of the Key Value Entity is persisted, without a history of changes.
          Changes are immediate and the latest state is available to consumers.
          Use Cases:
          Best used when you only care about the current state and do not need to track how that state was reached.
          Great for scenarios where state changes are frequent, and you want to simplify the state management.
          Summary
          Opt for Event Sourced Entities when you need to maintain a detailed history of state changes and perform operations based on those events.
          Choose Key Value Entities when you only need the latest state and prefer higher performance with minimal overhead.
      """.stripIndent()
    );

    for (int i = 0; i < userMessages.size(); i++) {
      componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::addInteraction)
        .invoke(
          new SessionMemoryEntity.AddInteractionCmd(
            new SessionMessage.UserMessage(
              Instant.now(),
              userMessages.get(i),
              "my-agent",
              Optional.empty()
            ),
            new SessionMessage.AiMessage(Instant.now(), aiMessages.get(i), "my-agent")
          )
        );
    }

    var history = componentClient
      .forEventSourcedEntity(sessionId)
      .method(SessionMemoryEntity::getHistory)
      .invoke(new SessionMemoryEntity.GetHistoryCmd());

    var summary = componentClient
      .forAgent()
      .inSession(sessionId)
      .method(CompactionAgent::summarizeSessionHistory)
      .invoke(history);

    // Result may look like this:
    // userMessage=
    // What are the core components of Akka and explain event sourced entities
    // in detail, including differences from key-value entities and when to use
    // each?,
    // aiMessage=
    // Akka comprises core components like Entities, Endpoints, Timed Actions,
    // Views, and Workflows for building applications. Event Sourced Entities
    // maintain state through event journals, featuring commands and snapshots
    // for efficiency. Key Value Entities store only the current state without history,
    // simplifying them for direct state needs. Use Event Sourced Entities to
    // track change history and Key Value Entities for performance when only the current
    // state matters.

    assertThat(summary.userMessage()).isNotEmpty();
    assertThat(summary.aiMessage()).isNotEmpty();
    assertThat(summary.userMessage().length()).isBetween(100, 1000);
    assertThat(summary.aiMessage().length()).isBetween(100, 1000);
  }
}
