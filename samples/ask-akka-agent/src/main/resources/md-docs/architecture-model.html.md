<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Project structure](architecture-model.html)

<!-- </nav> -->

# Project structure

Akka encourages a project structure that separates your system’s Application Programming Interfaces (APIs), Akka component logic, and business logic into different directories.

This structure supports a clear separation of concerns. It helps enable iterative development, testing in isolation, predictable packaging, and the ability to externalize configuration and static assets.

## <a href="about:blank#_akka_project_structure"></a> Akka project structure

A typical Akka project might have a layout like the following:

```txt
src/
 ├── main/
 │   ├── java/acme/planningagent/
 │   │   ├── api/           # External MCP, HTTP, gRPC endpoints
 │   │   ├── application/   # Akka components: Agents, Workflows, Entities, etc.
 │   │   └── domain/        # Business logic
 │   └── resources/
 └── test/
```

- The `api` directory exposes functionality to the outside world. This includes HTTP, gRPC, or MCP interfaces that forward requests to the application layer.
- The `application` directory contains the building blocks provided by Akka, implemented by you. It includes components such as `Agent`, `Entity`, `View`, `Workflow`, `Timer`, and `Consumer`.
- The `domain` directory holds plain Java classes that describe business rules and domain models. These are not tied to Akka or the runtime. Many use `record` to reduce boilerplate. You can test this logic without starting Akka or the runtime. This keeps the code focused and easier to maintain.
- The `resources` directory includes configuration files and other static content.
- The `test` directory contains unit and integration tests. Its structure mirrors `main` to make it easier to relate tests to the code they verify.
Keeping these areas distinct can help improve clarity and long-term maintainability. It also encourages testing and runtime separation.

## <a href="about:blank#_conceptual_layers"></a> Conceptual layers

The structure above also reflects a conceptual separation of responsibilities. These responsibilities can be thought of as layers. Business logic is central, with supporting code around it to enable runtime behavior and external interaction.

To maintain modularity:

- Avoid exposing domain types directly to the outside world.
- The API layer should not call the domain layer directly.
- Inner layers should not depend on or be aware of outer layers.
For more on coding structure and practical considerations, see the [coding guidelines](../java/ai-coding-assistant-guidelines.html).

### <a href="about:blank#_domain"></a> Domain

This layer contains business rules and domain concepts. It does not depend on Akka or other runtime concerns. These are plain Java classes, often using `record` to reduce boilerplate. Examples include logic to enforce limits, compute totals, or apply rules.

You can write unit tests for this layer without needing to start Akka or the runtime. The domain package remains isolated, focused, and easy to change.

### <a href="about:blank#_application"></a> Application

This layer connects the domain model to the Akka runtime. It contains the components that handle persistence, coordination, and external interaction. These components follow event-driven patterns and manage state in a way that supports consistency and responsiveness.

Most classes in this layer are based on Akka-provided building blocks. The domain logic remains in the inner layer. This layer makes it operational.

### <a href="about:blank#_api"></a> API

This layer connects your service to the outside world. It defines endpoints that expose application functionality over HTTP or gRPC. Requests are handled here and passed on to the application layer.

Endpoints use <a href="../java/component-and-service-calls.html#_component_client">`ComponentClient`</a> to call Akka components in the application layer. This maintains separation of concerns and ensures runtime boundaries are respected.

The API layer may also expose public event models over Kafka or other channels. External systems should interact with your service only through this layer.

Access control and request validation also belong here. For HTTP-specific guidance, see [Designing HTTP Endpoints](../java/http-endpoints.html).

## <a href="about:blank#_akka_services"></a> Akka Services

![Services](../_images/service.png)
A *Project* may contain multiple *Services*. Projects can be deployed to one or more regions to achieve geographic resilience. For details, see [Multi-region operations](multi-region.html).

## <a href="about:blank#_next_steps"></a> Next steps

Once familiar with the project structure, continue with:

- [Akka Deployment Model](deployment-model.html)
- [Development process](development-process.html)
- [Memory models](state-model.html)
- [Development best practices](../java/dev-best-practices.html)
You may also begin development right away using the [Akka SDK](../java/index.html).

<!-- <footer> -->
<!-- <nav> -->
[Distributed systems](distributed-systems.html) [Deployment model](deployment-model.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->