<!-- <nav> -->
- [Akka](../index.html)
- [Understanding](index.html)
- [Endpoints](grpc-vs-http-endpoints.html)

<!-- </nav> -->

# Endpoints

Endpoints components are how you expose your services to the outside world, be it another Akka service, external dependencies or client-facing applications. Two different types of endpoints are available: HTTP and gRPC Endpoints.

When designing APIs, choosing between HTTP and gRPC endpoints depends on the use case. This document outlines the key differences, strengths, and recommended usage for each approach.

## <a href="about:blank#_key_differences"></a> Key Differences

HTTP endpoints follow the traditional request-response model and are widely used for exposing APIs to external consumers. They use standard HTTP methods (GET, POST, PUT, DELETE, etc.) and typically exchange data in JSON format. This makes them accessible to web and mobile clients and easy to debug using common tools like browsers and API clients.

gRPC endpoints use the HTTP/2 protocol and Protobuf for data serialization, providing efficient and structured communication between services. They support various types of RPC calls, including unary and streaming, and are designed for high-performance, low-latency interactions. gRPC is commonly used for internal service-to-service communication due to its strong typing and schema evolution capabilities.

| Aspect | HTTP Endpoints | gRPC Endpoints |
| --- | --- | --- |
| Protocol | HTTP/1.1 or HTTP/2 | HTTP/2 |
| Serialization | JSON (text-based) | Protobuf (binary, compact) |
| Performance | Higher latency due to JSON parsing | Lower latency with efficient binary serialization |
| Streaming | Streaming from server (SSE) | Native bidirectional streaming |
| Tooling & Debugging | Easy to inspect and test with browser and other tools | Requires specialized tools due to binary format |
| Browser Support | Works natively in browsers | Requires gRPC-Web for use in browser |
| Backward Compatibility | Requires versioned endpoints or careful contract management | Supports schema evolution with Protobuf |

## <a href="about:blank#_when_to_use_http_endpoints"></a> When to use HTTP Endpoints

HTTP endpoints are recommended for client-facing APIs, including web and mobile apps. They offer broad compatibility, easy debugging, and integration with frontend frameworks.

Use HTTP endpoints when:

- The API is consumed directly by web browsers.
- Human readability and easy debugging are important.
- RESTful semantics, including standard HTTP methods and query parameters, are required.
- Familiarity among frontend developers is beneficial.

## <a href="about:blank#_when_to_use_grpc_endpoints"></a> When to use gRPC Endpoints

gRPC endpoints are recommended for service-to-service communication due to their efficiency and strong contract enforcement.

Use gRPC endpoints when:

- Services need to communicate with low latency and high throughput.
- Streaming (unary, client-streaming, server-streaming, bidirectional) is required.
- Backward and forward compatibility is essential for evolving services.
- Strongly typed service contracts are beneficial.

## <a href="about:blank#_next_steps"></a> Next Steps

For more information on designing and implementing HTTP and gRPC Endpoints in Akka, refer to the following guides:

- [Designing HTTP endpoints](../java/http-endpoints.html)
- [Designing gRPC Endpoints](../java/grpc-endpoints.html)
Additionally, note that both endpoint types can be secured using ACLs and JWTs, see:

- [Access Control Lists (ACLs)](../java/access-control.html)
- [JSON Web Tokens (JWT)](../java/auth-with-jwts.html)

<!-- <footer> -->
<!-- <nav> -->
[Saga patterns](saga-patterns.html) [Foundational AI concepts (video)](ai-concepts-video.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->