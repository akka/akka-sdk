<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Use cases](index.html)
- [APIs & exposure](apis-and-exposure.html)

<!-- </nav> -->

# APIs & exposure

Expose your agentic systems through a variety of endpoint types including REST, gRPC, MCP, and WebSocket interfaces. This pattern covers how to define typed APIs, add JWT-based authentication, generate OpenAPI specifications, and expose agents as MCP-compatible tool servers for integration with external AI systems.

## <a href="about:blank#_overview"></a> Overview

### <a href="about:blank#_when_to_use_this_pattern"></a> When to Use This Pattern

- You need to expose agents or services via REST APIs with OpenAPI documentation
- Your application requires gRPC endpoints for high-performance service-to-service communication
- You want to expose agents as MCP tool servers for integration with AI assistants and IDEs
- You need JWT-based authentication and authorization on your endpoints

### <a href="about:blank#_akka_components_involved"></a> Akka Components Involved

- **HTTP Endpoints** — REST APIs with OpenAPI generation and WebSocket support
- **gRPC Endpoints** — strongly-typed service-to-service communication
- **MCP Endpoints** — expose agents as Model Context Protocol tool servers

## <a href="about:blank#_sample_projects"></a> Sample Projects

The following sample projects demonstrate this pattern:

- [endpoint-jwt](https://github.com/akka-samples/endpoint-jwt) — HTTP endpoint with JWT-based authentication
- [trip-booking-with-tools](https://github.com/akka-samples/trip-agent) — agent exposed via multiple endpoint types with tool integration

## <a href="about:blank#_see_also"></a> See Also

- [Designing HTTP Endpoints](../http-endpoints.html)
- [Designing gRPC Endpoints](../grpc-endpoints.html)
- [Designing MCP Endpoints](../mcp-endpoints.html)

<!-- <footer> -->
<!-- <nav> -->
[Orchestration & durability](orchestration-and-durability.html) [Governance & compliance](governance-and-compliance.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->