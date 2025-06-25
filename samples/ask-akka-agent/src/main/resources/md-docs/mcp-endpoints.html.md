<!-- <nav> -->
- [Akka](../index.html)
- [Developing](index.html)
- [Components](components/index.html)
- [MCP Endpoints](mcp-endpoints.html)

<!-- </nav> -->

# Designing MCP Endpoints

![Endpoint](../_images/endpoint.png)
An Endpoint is a component that creates an externally accessible API. MCP Endpoints allow you to expose a services to MCP clients such as LLM chat agent desktop applications and agents running on other services.

MCP endpoints in Akka can provide

- "tools" – functions/logic the MCP client can call on behalf of the LLM
- "resources" – static resources or dynamic resource templates the MCP client can fetch for the LLM
- "prompts" - Template prompts created from input parameters
Endpoints are made available using a stateless Streamable HTTP transport defined by [MCP specification 2025-03-26](https://modelcontextprotocol.io/specification/2025-03-26).

## <a href="about:blank#_mcp_endpoint_class"></a> MCP endpoint class

To create an MCP endpoint, a class is annotated with `@McpEndpoint` and ACL configuring where it can be accessed from.

[ExampleMcpEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleMcpEndpoint.java)
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpEndpoint(
    serverName = "doc-snippets-mcp-sample",
    serverVersion = "0.0.1")
public class ExampleMcpEndpoint {
  private ComponentClient componentClient;

  public ExampleMcpEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }
}
```
The service is available under the path `/mcp` by default, but it is possible to have multiple MCP endpoints in the same
Akka by specifying.

### <a href="about:blank#_tools"></a> Tools

A tool is a public method made available to MCP clients.

It is important to give a clear description of what the tool does using the description value as well as using the `@Description` annotation on parameters and fields, since this is how the
calling LLM gains an understanding of what the tool does.

By default, the input schema for the tool is reflectively created based on the input parameter type. The input class may require
additional information per field to help the LLM understand what each parameter means.

Only simple input parameter classes are supported. Fields must be of primitive type, the boxed Java primitive types or strings.

All fields are marked as required in the schema by default, any non-required parameter should be of type `Optional<T>`

[ExampleMcpEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleMcpEndpoint.java)
```java
@McpTool(
      name = "add", // (1)
      description = "Adds the two given numbers and returns the result" // (2)
  )
  public String add(@Description("The first number") int n1, @Description("The second number") int n2) { // (3)
    var result = n1 + n2;
    return Integer.toString(result);
  }
```

| **1** | An optional tool name. If not defined, the method name is used. Must be unique in the MCP service if defined |
| **2** | A description about what the tool does |
| **3** | The `Description` annotations describing each input for the tool. |
For full flexibility and more complex input types, it is also possible to specify the JSON Schema of the input manually in the annotation:

[ExampleMcpEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleMcpEndpoint.java)
```java
public record EchoToolRequest(String message) {}

  @McpTool(
      name = "echo",
      description = "Echoes back whatever string is thrown at it",
      inputSchema = """
      {
        "type":"object",
        "properties": {
          "input": {
            "type": "object",
            "properties": {
              "message": {"type":"string", "description":"A string to echo"}
            },
            "required": ["message"]
          }
         },
         "required": ["input"]
      }
      """ // (1)
    )
  public String echo(EchoToolRequest input) {
    return input.message;
  }
```

| **1** | The entire JSON Schema string for the input |
When using a manual schema, it is crucial to make sure that the schema is describing a JSON structure that
is actually what is accepted when Jackson parses it into the input parameter type.

### <a href="about:blank#_resources"></a> Resources

A static resource is a public zero-parameter method returning text or bytes. The resource is identified by a unique URI.

[ExampleMcpEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleMcpEndpoint.java)
```java
@McpResource(
      uri = "file:///background.png", // (1)
      name = "Background image",
      description = "A background image for Akka sites", mimeType = "image/png")
  public byte[] backgroundImage() { // (2)
    try (InputStream in = this.getClass().getResourceAsStream("/static-resources/images/background.png")) {
      if (in == null) throw new RuntimeException("Could not find background image");
      return in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
```

| **1** | A URI identifying the specific resource returned by this method |
| **2** | Empty parameter list, a return type that is `String` for raw text content, `byte[]` for byte contents. Other return types are turned into JSON. |
A dynamic resource instead defines a URI template with placeholders for sections, the method accepts `String` parameters with the same names.

[ExampleMcpEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleMcpEndpoint.java)
```java
@McpResource(
      uriTemplate = "file:///images/{fileName}", // (1)
      name = "Dynamic file",
      description = "Fetch a specific image file",
      // Note: there is no way to dynamically return a mime type, it has to be the same for all files
      mimeType = "image/png")
  public byte[] dynamicResource(String fileName) { // (2)
    if (fileName.contains("..")) {
      // Important to validate input
      throw new RuntimeException("Invalid image file: " + fileName);
    }
    try (InputStream in = this.getClass().getResourceAsStream("/static-resources/images/" + fileName)) {
      if (in == null) throw new RuntimeException("Could not find background image");
      return in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
```

| **1** | A URI template with placeholders |
| **2** | A parameter list matching the placeholders. |

### <a href="about:blank#_prompts"></a> Prompts

Prompts are a way to provide example prompts to the MCP client given some input parameters.

[ExampleMcpEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/api/ExampleMcpEndpoint.java)
```java
@McpPrompt(description = "Java code review prompt")
  public String javaCodeReview(@Description("The Java code to review") String code) { // (1)
    return "Please review this Java code:\\n" + code; // (2)
  }
```

| **1** | Zero or more string parameters to use in the prompts, annotated with `@Description` to describe the purpose of each |
| **2** | Logic to use the input to construct a prompt |

## <a href="about:blank#_interacting_with_other_components"></a> Interacting with other components

The most common use case for endpoints is to interact with other components in a service. This is done through
the `akka.javasdk.client.ComponentClient`. If the constructor of the endpoint class has a parameter of this type,
it will be injected by the SDK.

For more details see [Component and service calls](component-and-service-calls.html)

## <a href="about:blank#_interacting_with_http_services"></a> Interacting with HTTP services

It is possible for an MCP endpoint to interact with other services over HTTP. This is done through the `akka.javasdk.http.HttpClientProvider`.

For more details see [Component and service calls](component-and-service-calls.html)

## <a href="about:blank#_authentication_and_authorization"></a> Authentication and authorization

The Akka MCP endpoints do not support the OAuth 2.1 flows in the MCP spec.

Endpoint classes can be annotated using the `@ACL` annotations and `@JWT` to control access (individual method annotations are not supported).

It is also possible to access endpoint request headers for custom authorization based on headers.

## <a href="about:blank#_testing_mcp_endpoints"></a> Testing MCP endpoints

There are no specific test kit utilities for MCP. However, it is possible to manually construct endpoints and directly
call the methods as well as use the testkit HTTP client together with handcrafted JSON-RPC MCP payloads to exercise
MCP tools, prompts and resources.

<!-- <footer> -->
<!-- <nav> -->
[gRPC Endpoints](grpc-endpoints.html) [Views](views.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->