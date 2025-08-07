<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Delegation with Effects](declarative-effects.html)

<!-- </nav> -->

# Delegation with Effects

In Akka, the behavior of your services is decoupled from the execution. This decoupling allows Akka to determine how a service is executed without being constrained by how your system’s behavior is defined. Delegation removes you from worrying about distributed systems, persistence, elasticity, or networking. With Akka’s hosted services, we use delegation to enable swapping out new, improved runtimes while your services are running without a recompilation or redeployment!

In Akka, you specify *what* the system should do, while the Akka runtime decides *how* it should be executed. For example, you define an agent by specifying the model it uses, its session memory, and the user prompt. This represents the *what*. The Akka runtime then determines the *how* by managing processes, virtual threads, persistence, and actor-based concurrency.

![Akka Agentic Platform](_images/component-effects.png)


Your services define the *what* using `Effects`, which are Application Programming Interfaces (APIs) provided by each Akka component. When you write a component method, you return an `Effect<…​>` object that describes, in a declarative way, what you want Akka to do.

For example, when using Akka’s [Agent](../java/agents.html) component, you might return an `Effect` that tells the runtime to execute the agent with a system message, a user message, and then send the AI model’s response back to the requester:

```java
public Effect<String> query(String question) {
    return effects()
      .systemMessage("You are a helpful...")
      .userMessage(question)
      .thenReply();
  }
```
Each component defines its own Effect API offering predefined operations tailored to the component’s specific semantics. For example, [Event Sourced Entities](../java/event-sourced-entities.html) provide an Effect for persisting events, while a [Workflow](../java/workflows.html) Effect defines both what needs to be executed and how to handle the result to transition to the next step.

This model simplifies development by removing the need to handle persistence, distribution, serialization, cache management, replication, and other distributed system concerns. Developers can focus on business logic — defining what needs to be persisted, how to respond to the caller, transitioning to different steps, rejecting commands, and more — while the Akka runtime takes care of the rest.

For example, with our Workflow component at the end of each step, you return an Effect that indicates how the Workflow should persist the call stack and which stage it should transition to next.

```java
return effects()
   .updateState(currentState().withStatus(WITHDRAW_SUCCEED))
   .transitionTo("deposit", depositInput);
```
For details on the specific Effect types, refer to the documentation for each component.

| Component | Available Effects |
| --- | --- |
| [Agents](../java/agents.html#_effect_api) | Model, Memory, Tools, System and User Message,  Reply, Error |
| [Event Sourced Entities](../java/event-sourced-entities.html#_effect_api) | Persist Events, Reply, Delete Entity, Error |
| [Key Value Entities](../java/key-value-entities.html#_effect_api) | Update State, Reply, Delete State, Error |
| [Views](../java/views.html#_effect_api) | Update State, Delete State, Ignore |
| [Workflows](../java/workflows.html#_effect_api) | Update State, Transition, Pause, End, Reject Command, Reply |
| [Timers](../java/timed-actions.html#_effect_api) | Confirm Scheduled Call, Error |
| [Consumers](../java/consuming-producing.html#_effect_api) | Publish to Topic, Confirm Message, Ignore |

<!-- <footer> -->
<!-- <nav> -->
[Development process](development-process.html) [Entity state models](state-model.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->