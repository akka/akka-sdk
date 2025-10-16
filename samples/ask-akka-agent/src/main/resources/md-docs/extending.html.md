<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Extending with function tools](extending.html)

<!-- </nav> -->

# Extending agents with function tools

You may frequently hear people say things like "the LLM can make a call" or "the LLM can use a tool". While these statements get the point across, they’re not entirely accurate. In truth, the agent will tell the LLM which *tools* are available for use. The LLM then determines from the prompt which tools it needs to call and with which parameters.

The Agent will then in turn execute the tool requested by the LLM, incorporate the tool results into the session context, and then send a new prompt. This will continue in a loop until the LLM no longer indicates it needs to invoke a tool to perform its task.

There are three ways to add function tools to your agent:

1. **Agent-defined function tools** — Define function tools directly within your agent class using the `@FunctionTool` annotation. These are automatically registered as available tools for the current Agent.
2. **Externally defined function tools** — Explicitly register external objects or classes containing function tools by
passing them to the `effects().tools()` method in your agent’s command handler. Objects or classes passed to `effects
().tools()` must have at least one public method annotated with `@FunctionTool`.
3. **Tools defined by remote MCP servers** – Register remote MCP servers to let the agent use tools they provide.

|  | A class (either the agent itself or an external tool class) can have multiple methods annotated with `@FunctionTool`. Each annotated method will be registered as a separate tool that the LLM can choose to invoke based on the task requirements. |
You can use either approach independently or combine them based on your needs. Let’s look at a complete example showing both approaches:

[WeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherAgent.java)
```java
public class WeatherAgent extends Agent {

  private final WeatherService weatherService;

  public WeatherAgent(WeatherService weatherService) {
    this.weatherService = weatherService; // (1)
  }

  public Effect<String> query(AgentRequest request) {
    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .tools(weatherService) // (2)
      .userMessage(request.message())
      .thenReply();
  }

  @FunctionTool(description = "Return current date in yyyy-MM-dd format") // (3)
  private String getCurrentDate() {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }
}
```

| **1** | The `WeatherService` providing a function tool is injected into the agent (see [DependencyProvider](../setup-and-dependency-injection.html#_custom_dependency_injection)). |
| **2** | We explicitly register the `weatherService` using the `tools()` method to make its method available as a tool for
the current Agent. |
| **3** | We define a simple tool directly in the agent class using the `@FunctionTool` annotation, which is implicitly registered. Note that since this method is defined in the agent itself, it can even be a private method. |
The `WeatherService` is an interface with a method annotated with `@FunctionTool`. A concrete implementation of this
interface is provided by `WeatherServiceImpl` class.
This class is made available for injection in the service setup using a [DependencyProvider](../setup-and-dependency-injection.html#_custom_dependency_injection).

[WeatherService.java](https://github.com/akka/akka-sdk/blob/main/samples/multi-agent/src/main/java/demo/multiagent/application/WeatherService.java)
```java
public interface WeatherService {
  @FunctionTool(description = "Returns the weather forecast for a given city.") // (1)
  String getWeather(
    @Description("A location or city name.") String location, // (2)
    @Description("Forecast for a given date, in yyyy-MM-dd format.") Optional<String> date
  ); // (3)
}
```

| **1** | Annotate method with `@FunctionTool` and provide a clear description of what it does. |
| **2** | Parameters can be documented with the `@Description` annotation to help the LLM understand how to use them. |
| **3** | The date parameter is optional. The LLM may call `getCurrentDate` first or call this method without a date, depending on the user query. |

|  | LLMs are all about context. The more context you can provide, the better the results.
Both `@FunctionTool` and `@Description` annotations are used to provide context to the LLM about the tool function and its parameters.
The better the context, the better the LLM can understand what the tool function does and how to use it. |
In this example, the agent has access to both:

- The `getCurrentDate()` method defined within the agent class (implicitly registered via annotation)
- The `getWeather()` method defined in the `WeatherService` interface (explicitly registered via the `.tools()` method)

## <a href="about:blank#_sharing_function_tools_across_agents"></a> Sharing function tools across agents

Function tools defined in external classes can be shared and reused across multiple agents. This approach promotes code reusability and helps maintain a consistent behavior for common functionalities.

When a tool like `WeatherService` is shared across multiple agents:

- Each agent can register the same tool but use it in different contexts
- The tool behavior remains consistent, but how and when agents invoke it may differ based on their specific tasks
- Agents provide different system prompts that influence how the LLM decides to use the shared tool

## <a href="about:blank#_lazy_initialization_of_tool_classes"></a> Lazy initialization of tool classes

In the example above, we pass an instance of `WeatherService` to the `tools()` method. Alternatively, you can pass the `Class` object instead:

java]
```java
public Effect<AgentResponse> query(String message) {
  return effects()
    .systemMessage(SYSTEM_MESSAGE)
    .tools(WeatherService.class) // (1)
    .userMessage(message)
    .responseAs(AgentResponse.class)
    .thenReply();
}
```

| **1** | The WeatherService is passed as a `Class` instead of an instance. It will be instantiated when the agent needs to use it. |
When you pass a `Class` instead of an instance, the class is only instantiated when the agent actually needs to use the tool.

For this approach to work, you must register the class with a [DependencyProvider](../setup-and-dependency-injection.html#_custom_dependency_injection) in your service setup. The DependencyProvider is responsible for creating and managing instances of these classes when they’re needed. This gives you complete control over how tool dependencies are instantiated and managed throughout your application.

## <a href="about:blank#_using_tools_from_remote_mcp_servers"></a> Using tools from remote MCP servers

[Akka MCP endpoints](../mcp-endpoints.html) declared in other services, or third party MCP services can be added to
the agent. By default, all tools provided by each added remote MCP server are included, but it is possible to filter
available tools from each server based on their name.

It is also possible to intercept, modify, or deny MCP tool requests, or their responses by defining a `RemoteMcpTools.ToolInterceptor`.

[RemoteMcpWeatherAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/RemoteMcpWeatherAgent.java)
```java
public Effect<AgentResponse> query(String message) {
  return effects()
    .systemMessage(SYSTEM_MESSAGE)
    .mcpTools(
      RemoteMcpTools.fromService("weather-service"), // (1)
      RemoteMcpTools.fromServer("https://weather.example.com/mcp") // (2)
        .addClientHeader(Authorization.oauth2(System.getenv("WEATHER_API_TOKEN"))) // (3)
        .withAllowedToolNames(Set.of("get_weather")) // (4)
    )
    .userMessage(message)
    .responseAs(AgentResponse.class)
    .thenReply();
}
```

| **1** | For MCP endpoints in other Akka services, use HTTP and the deployed service name |
| **2** | For third party MCP servers use the fully qualified host name and make sure to use HTTPS as the requests will
go over the public internet. |
| **3** | Custom headers to pass along can be defined |
| **4** | As well as filters of what tools to allow. |
When using MCP endpoints in other Akka services, the service ACLs apply just like for [HTTP endpoints](../http-endpoints.html) and [gRPC endpoints](../grpc-endpoints.html).

## <a href="about:blank#configuring_tool_call_limits"></a> Configuring tool call limits

Inside a single request/response cycle, an LLM can successively request the agent to call functions tools or MCP tools. After analyzing the result of a call, the LLM might decide to request another call to gather more context. The `akka.javasdk.agent.max-tool-call-steps` setting limits how many such steps may occur between a user request and the final AI response.

By default, this value is set to 100. You can adjust this in your configuration:

application.conf
```hocon
# Increase the limit to allow more tool calls
akka.javasdk.agent.max-tool-call-steps = 150
```

<!-- <footer> -->
<!-- <nav> -->
[Handling failures](failures.html) [Streaming responses](streaming.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->