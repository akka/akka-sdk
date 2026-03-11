<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Managing session memory](memory.html)

<!-- </nav> -->

# Managing session memory

Session Memory provides a history mechanism that enables agents to maintain context across multiple interactions. This feature is essential for building agents that can remember previous exchanges with users, understand context, and provide coherent responses over time.

When an agent interacts with an AI model, both the user message and the AI response are automatically stored in the session memory. These messages are then included as additional context in subsequent requests to the model, allowing it to reference previous parts of the interaction.

The session memory is:

- Identified by a session ID that links related interactions
- Shared between multiple agents if they use the same session ID
- Persisted as an event-sourced entity
- Automatically managed by the Agent

## <a href="about:blank#_session_memory_configuration"></a> Session memory configuration

By default, session memory is enabled for all agents. You can configure it globally in your `application.conf`:

```conf
akka.javasdk.agent.memory {
  enabled = true
  limited-window {
    max-size = 156KiB # max history size before oldest message start being removed
  }
}
```
Or you can configure memory behavior for specific agent interactions using the `MemoryProvider` API.

Example with `limitedWindow` memory provider:

```java
public Effect<String> ask(String question) {
  return effects()
    .memory(MemoryProvider.limitedWindow().readLast// (5))
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```
Example disabling session memory for the agent:

```java
public Effect<String> ask(String question) {
  return effects()
    .memory(MemoryProvider.none())
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```

## <a href="about:blank#_different_memory_providers"></a> Different memory providers

The <a href="../_attachments/api/akka/javasdk/agent/MemoryProvider.html">`MemoryProvider`</a> interface allows you to control how session memory behaves:

- `MemoryProvider.none()` - Disables both reading from and writing to session memory
- `MemoryProvider.limitedWindow()` - Configures memory with options to, e.g.:

  - Setup **read only** memory, in which the agent reads the memory but does not allow write any interactions to it. This is ideal for multi-agent sessions where some agents can store memory and others can’t.
  - Setup **write only** memory, in which the agent register the interactions to the session memory but does not take those in consideration when processing the user message.
  - Limit the amount of messages used as context in each interaction, i.e. use only the last N number of messages for context (good for token usage control).
  - Apply **filters** to selectively include or exclude messages based on agent component ID or role.
- `MemoryProvider.custom()` - Allows you to provide a custom implementation for the `SessionMemory` interface and store the session memory externally in a database / service of your preference.

### <a href="about:blank#_filtering_memory"></a> Filtering memory

In multi-agent scenarios, you may want to control which messages from the session history are visible to specific agents. The <a href="../_attachments/api/akka/javasdk/agent/MemoryFilter.html">`MemoryFilter`</a> API allows you to filter messages based on the agent component ID or role that produced them.

The `MemoryFilter` API uses a fluent builder pattern that allows you to chain multiple filters together. When multiple filters are chained, filters of the same type are automatically merged together. The merged filters are then applied in the order that each filter type first appears in the chain, with each filter type operating on the result of the previous filter type.

#### <a href="about:blank#_filter_by_agent_component_id"></a> Filter by agent component ID

You can include only messages from specific agents:

```java
public Effect<String> ask(String question) {
  return effects()
    .memory(
      MemoryProvider.limitedWindow()
        .filtered(MemoryFilter.includeFromAgentId("summarizer-agent")) // (1)
    )
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```

| **1** | Only messages from the "summarizer-agent" will be included in the context. |
Or exclude messages from specific agents:

```java
public Effect<String> ask(String question) {
  return effects()
    .memory(
      MemoryProvider.limitedWindow()
        .filtered(MemoryFilter.excludeFromAgentRole("internal")) // (1)
    )
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```

| **1** | Messages from agents with the "internal" role will be excluded from the context. |

#### <a href="about:blank#_combining_multiple_filters"></a> Combining multiple filters

You can chain multiple filters together using the fluent builder API. Filters of the same type (Include or Exclude) are automatically merged:

```java
public Effect<String> ask(String question) {
  return effects()
    .memory(
      MemoryProvider.limitedWindow()
        .filtered(
          MemoryFilter.includeFromAgentId("activity-agent").includeFromAgentId(
            "weather-agent"
          ) // (1)
        )
    )
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```

| **1** | The two `includeFromAgentId` calls are merged into a single Include filter that includes messages from both "weather-agent"
and "activity-agent". This filter works as an OR clause: it includes all messages generated by "weather-agent" or
by "activity-agent". |
You can also combine agent IDs and roles in the same filter chain:

```java
var filter = MemoryFilter.includeFromAgentId("weather-agent")
    .includeFromAgentRole("summarizer");
```
This creates a single Include filter that includes messages from "weather-agent" OR messages with the "summarizer" role (regardless of which agent produced them).

#### <a href="about:blank#_combining_filters_with_other_options"></a> Combining filters with other options

Filters can be combined with other memory provider options, such as limiting the number of messages:

```java
public Effect<String> ask(String question) {
  return effects()
    .memory(
      MemoryProvider.limitedWindow()
        .readLast(10, MemoryFilter.excludeFromAgentId("debug-agent")) // (1)
    )
    .systemMessage("You are a helpful...")
    .userMessage(question)
    .thenReply();
}
```

| **1** | Read the last 10 messages, excluding those from the "debug-agent". |
When combining filters with `readLast()`, the filters are applied first to select matching messages, and then the limit is enforced on the filtered results.

#### <a href="about:blank#_available_filter_types"></a> Available filter types

The `MemoryFilter` interface provides several static factory methods that return a `MemoryFilterSupplier`. This supplier implements a fluent builder pattern, allowing you to chain additional filters:

- `MemoryFilter.includeFromAgentId(String id)` - Include only messages from the specified agent component ID
- `MemoryFilter.excludeFromAgentId(String id)` - Exclude messages from the specified agent component ID
- `MemoryFilter.includeFromAgentRole(String role)` - Include only messages from agents with the specified role
- `MemoryFilter.excludeFromAgentRole(String role)` - Exclude messages from agents with the specified role
Each of these methods can be called on the returned supplier to chain additional filters. The supplier can then be passed directly to methods like `filtered()`, `readOnly()`, or `readLast()`.

##### <a href="about:blank#_filter_merging_behavior"></a> Filter merging behavior

When you chain multiple filter operations of the same type, they are automatically merged:

**Include filters** use OR logic: A message is included if it matches ANY of the specified criteria (agent ID OR role).

Example:

```java
var filter = MemoryFilter.includeFromAgentId("agent-1")
    .includeFromAgentId("agent-2")
    .includeFromAgentRole("summarizer");
```
This creates a single Include filter that will include messages from "agent-1" OR "agent-2" OR messages with the "summarizer" role.

**Exclude filters** also use OR logic for exclusion: A message is excluded if it matches ANY of the specified criteria (agent ID OR role). A message is only included if it matches NONE of the exclusion criteria.

Example:

```java
var filter = MemoryFilter.excludeFromAgentId("debug-agent")
    .excludeFromAgentRole("internal");
```
This creates a single Exclude filter that will exclude messages from "debug-agent" OR messages with the "internal" role. Only messages that don’t match either criterion will be included.

## <a href="about:blank#_accessing_session_memory"></a> Accessing session memory

The default implementation of Session Memory is backed by a regular [Event Sourced Entity](../event-sourced-entities.html) called `SessionMemoryEntity`, which allows you to interact directly with it as you would do with any other entities in your application. This includes the possibility to directly modify or access it through the `ComponentClient` but also the ability to subscribe to changes in the session memory, as shown below:

[SessionMemoryConsumer.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/SessionMemoryConsumer.java)
```java
@Component(id = "session-memory-consumer")
@Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
public class SessionMemoryConsumer extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(getClass());


  public Effect onSessionMemoryEvent(SessionMemoryEntity.Event event) {
    var sessionId = messageContext().eventSubject().get();

    switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded userMsg -> logger.info(
        "User message added to session {}: {}",
        sessionId,
        userMsg.message()
      );
      // ...

      default -> logger.debug("Unhandled session memory event: {}", event);
    }

    return effects().done();
  }
}
```
This can be useful for more granular control over token usage but also to allow external integrations and analytics over these details.

## <a href="about:blank#_compaction"></a> Compaction

You can update the session memory to reduce the size of the history. One technique is to let an LLM summarize the interaction history and use the new summary instead of the full history. Such agent can look like this:

[CompactionAgent.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/CompactionAgent.java)
```java
@Component(id = "compaction-agent")
public class CompactionAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You can compact an interaction history with an LLM. From the given
    USER, TOOL_CALL_RESPONSE and AI messages you create one single
    user message and one single ai message.

    The interaction history starts with USER: followed by the user message.
    For each user message there is a corresponding response for AI that starts with AI:
    Keep the original style of user question and AI answer in the summary.

    Note that AI messages may contain TOOL_CALL_REQUEST(S) and
    be followed by TOOL_CALL_RESPONSE(S).
    Make sure to keep this information in the generated ai message.
    Do not keep it as structured tool calls, but make sure to extract
    the relevant context.

    Your response should follow a strict json schema as defined bellow.
    {
      "userMessage": "<the user message summary>",
      "aiMessage: "<the AI message summary>",
    }

    Do not include any explanations or text outside of the JSON structure.
    """.stripIndent(); // (1)

  public record Result(String userMessage, String aiMessage) {}

  private final ComponentClient componentClient;

  public CompactionAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<Result> summarizeSessionHistory(SessionHistory history) { // (2)
    String concatenatedMessages = history
      .messages()
      .stream()
      .map(msg -> {
        return switch (msg) {
          case SessionMessage.UserMessage userMsg -> "\n\nUSER:\n" + userMsg.text(); // (3)
          case SessionMessage.AiMessage aiMessage -> {
            var aiText = "\n\nAI:\n" + aiMessage.text();
            yield aiMessage
              .toolCallRequests()
              .stream()
              .reduce(
                aiText,
                // if there are tool requests, also append them to the aiText
                (acc, req) ->
                  acc +
                  "\n\tTOOL_CALL_REQUEST: id=" +
                  req.id() +
                  ", name=" +
                  req.name() +
                  ", args=" +
                  req.arguments() +
                  " \n",
                String::concat
              );
          }
          case SessionMessage.ToolCallResponse toolRes -> "\n\nTOOL_CALL_RESPONSE:\n" +
          toolRes.text();
        };
      })
      .collect(Collectors.joining()); // (3)

    return effects()
      .memory(MemoryProvider.none()) // (4)
      .model(
        ModelProvider.openAi()
          .withModelName("gpt-4o-mini")
          .withApiKey(System.getenv("OPENAI_API_KEY"))
          .withMaxTokens// (1000)
      )
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(concatenatedMessages)
      .responseAs(Result.class)
      .thenReply();
  }
}
```

| **1** | Instructions to create the summary of user and AI messages and result as JSON. |
| **2** | The full history from the `SessionMemoryEntity`. |
| **3** | Format and concatenate the messages. |
| **4** | The `CompactionAgent` itself doesn’t need any session memory. |
One way to trigger compaction is to use a consumer of the session memory events and call the `CompactionAgent` from that consumer when a threshold is exceeded.

[SessionMemoryConsumer.java](https://github.com/akka/akka-sdk/blob/main/samples/doc-snippets/src/main/java/com/example/application/SessionMemoryConsumer.java)
```java
@Component(id = "session-memory-consumer")
@Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
public class SessionMemoryConsumer extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ComponentClient componentClient;

  public SessionMemoryConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }


  public Effect onSessionMemoryEvent(SessionMemoryEntity.Event event) {
    var sessionId = messageContext().eventSubject().get();

    switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded userMsg -> logger.info(
        "User message added to session {}: {}",
        sessionId,
        userMsg.message()
      );
      // ...
      case SessionMemoryEntity.Event.AiMessageAdded aiMsg -> {
        if (aiMsg.historySizeInBytes() > 100000) { // (1)
          var history = componentClient
            .forEventSourcedEntity(sessionId)
            .method(SessionMemoryEntity::getHistory) // (2)
            .invoke(new SessionMemoryEntity.GetHistoryCmd());

          AgentReply<CompactionAgent.Result> summaryReply = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(CompactionAgent::summarizeSessionHistory) // (3)
            .withDetailedReply()
            .invoke(history);

          var now = Instant.now();
          var tokenUsage = new SessionMessage.TokenUsage(
            summaryReply.tokenUsage().inputTokens(),
            summaryReply.tokenUsage().outputTokens()
          );

          componentClient
            .forEventSourcedEntity(sessionId)
            .method(SessionMemoryEntity::compactHistory) // (4)
            .invoke(
              new SessionMemoryEntity.CompactionCmd(
                new SessionMessage.UserMessage(now, summaryReply.value().userMessage(), ""),
                new SessionMessage.AiMessage(
                  now,
                  summaryReply.value().aiMessage(),
                  "",
                  tokenUsage
                ), // (5)
                history.sequenceNumber() // (6)
              )
            );
        }
      }

      default -> logger.debug("Unhandled session memory event: {}", event);
    }

    return effects().done();
  }
}
```

| **1** | The AiMessageAdded has the total size of the history. |
| **2** | Retrieve the full history from the `SessionMemoryEntity`. |
| **3** | Call the agent to make the summary. |
| **4** | Store the summary as the new compacted history in the `SessionMemoryEntity`. |
| **5** | Set token usage for the AiMessage based on compaction summary reply. |
| **6** | To support concurrent updates, the `sequenceNumber` of the retrieved history is included in the `CompactionCmd`. |

## <a href="about:blank#_multi_region_replication"></a> Multi-region replication

The session memory can be replicated to other regions, but it has the multi-region replication filter enabled to only include the local region when using `request-region` primary selection. When accessed from another region the filter will automatically be expanded to include the other region too, and thereby contain the same information.

<!-- <footer> -->
<!-- <nav> -->
[Calling agents](calling.html) [Structured responses](structured.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->