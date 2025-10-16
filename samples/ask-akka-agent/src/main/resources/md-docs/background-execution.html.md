<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Background execution](background-execution.html)

<!-- </nav> -->

# Background execution

In Akka, [effects](../reference/glossary.html#effect) are processed in the background. When you call a component or a service within Akka, the default mode is synchronous, but you can opt-in to asynchronous for more control. You do not need to implement any asynchronous libraries, queues, promises, callbacks, await/async, futures, or event loops for Akka to behave this way.

Akka handles background processing using actors. Actors offer a lightweight model for concurrency, relying on asynchronous messaging rather than locks. This helps avoid shared mutable state and sidesteps many of the typical issues seen in multi-threaded programming, such as blocking, deadlocks, and race conditions.

Because the Actor runtime manages concurrency, you can write simple, synchronous code within your Akka components. There is little need to worry about performance or resource contention.

This "share nothing" approach also makes it easier to reason about concurrent systems. It helps reduce the chance of deadlocks and supports the creation of systems that are more stable and easier to scale. Akka also includes built-in supervision and fault tolerance, so if an actor fails, the issue is contained and resolved locally. This avoids broader system failure and reduces the need for complex manual error handling, which is often required elsewhere.

When you use the [component client](../reference/glossary.html#component_client) to call another component, your code remains synchronous and returns regular objects. If those calls involve effects, the Akka runtime takes care of them in the background. Depending on where the component is located, the runtime may even do so across different locations.

<!-- <footer> -->
<!-- <nav> -->
[Development process](development-process.html) [Delegation with Effects](declarative-effects.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->