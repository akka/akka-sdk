<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Declarative effects](declarative-effects.html)

<!-- </nav> -->

# Declarative effects

Effects are declarative APIs that describes the actions the Akka runtime needs to perform. It is inherently lazy, acting as a blueprint of operations to be executed. Once passed to the Akka runtime, the Effect is executed, resulting in the requested system changes. Effect APIs are essential for implementing components, bridging your application logic with the Akka runtime.

Each [application layer component](architecture-model.html#_application_layer) defines its own Effect API offering predefined operations tailored to the component specific semantics. For example, [Event Sourced Entities](../java/event-sourced-entities.html) provide an `Effect` for persisting events, while a [Workflow](../java/workflows.html) Effect defines both what needs to be executed and how to handle the result to transition to the next step.

Think of it as a dialogue between your application components and the Akka runtime. The component implementation processes incoming commands, executes the necessary business logic, and returns a description of what needs to be done. The Akka runtime then executes the effect and manages all necessary operations to ensure responsiveness and resilience.

This model simplifies development by removing the need to handle persistence, distribution, serialization, cache management, replication, and other distributed system concerns. Developers can focus on business logic — defining what needs to be persisted, how to respond to the caller, transitioning to different steps, rejecting commands, and more — while the Akka runtime takes care of the rest.

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