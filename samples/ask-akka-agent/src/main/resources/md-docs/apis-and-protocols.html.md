<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Integrations](index.html)
- [APIs & protocols](apis-and-protocols.html)

<!-- </nav> -->

# APIs & protocols

Akka provides built-in support for exposing your services through multiple protocols.

## <a href="about:blank#_http"></a> HTTP

- **REST APIs** — Define JSON-based HTTP endpoints with routing, request/response handling, and access control. See [HTTP Endpoints](../http-endpoints.html).
- **OpenAPI** — Schema generation is built in. See [OpenAPI Endpoint schema](../http-endpoints.html#_openapi_endpoint_schema).
- **WebSockets** — Real-time bidirectional communication is built in. See [WebSocket support](../http-endpoints.html#websocket).

## <a href="about:blank#_grpc"></a> gRPC

High-performance APIs with protocol buffer definitions. See [gRPC Endpoints](../grpc-endpoints.html).

## <a href="about:blank#_mcp_model_context_protocol"></a> MCP (Model Context Protocol)

- **MCP Endpoints** — Expose Agent functions to remote LLMs via `@FunctionTool` annotations. See [MCP Endpoints](../mcp-endpoints.html).
- **MCP CLI server** — `akka mcp serve` exposes CLI operations as MCP tools for Claude Desktop, VS Code, and Cursor.

## <a href="about:blank#_agent_protocol_support"></a> Agent protocol support

A2A, ACP, and MCP clients are baked into Akka’s Agent component. Agents natively consume tools and communicate with other Agents via all three protocols. No external libraries needed.

- **A2A (Agent-to-Agent)** — Google’s protocol for agent-to-agent communication
- **ACP (Agent Communication Protocol)** — Linux Foundation protocol for agent communication
- **MCP (Model Context Protocol)** — Anthropic’s protocol for connecting AI models to external tools
[MCP](https://akka.io/blog/mcp-a2a-acp-what-does-it-all-mean)

## <a href="about:blank#_see_also"></a> See also

- [HTTP Endpoints](../http-endpoints.html)
- [gRPC Endpoints](../grpc-endpoints.html)
- [MCP Endpoints](../mcp-endpoints.html)
- [Agents](../agents.html)

<!-- <footer> -->
<!-- <nav> -->
[Messaging & events](messaging-and-events.html) [Identity & security](identity-and-security.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->