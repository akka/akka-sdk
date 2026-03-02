<!-- <nav> -->
- [Akka](../../index.html)
- [Developing](../index.html)
- [Components](../components/index.html)
- [Agents](../agents.html)
- [Streaming responses](streaming.html)

<!-- </nav> -->

# Streaming responses

In AI chat applications, you’ve seen how responses are displayed word by word as they are generated. There are a few reasons for this. The first is that LLMs are *prediction* engines. Each time a token (usually a word) is streamed to the response, the LLM will attempt to *predict* the next word in the output. This causes the small delays between words.

The other reason why responses are streamed is that it can take a very long time to generate the full response, so the user experience is much better getting the answer as a live stream of tokens. To support this real-time user experience, the agent can stream the model response tokens to an endpoint. These tokens can then be pushed to the client using server-sent events (SSE).

```java
@Component(id = "streaming-activity-agent")
public class StreamingActivityAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
    """
    You are an activity agent. Your job is to suggest activities in the
    real world. Like for example, a team building activity, sports, an
    indoor or outdoor game, board games, a city trip, etc.
    """.stripIndent();

  public StreamEffect query(String message) { // (1)
    return streamEffects() // (2)
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(message)
      .thenReply();
  }
}
```

| **1** | The method returns `StreamEffect` instead of `Effect<T>`. |
| **2** | Use the `streamEffects()` builder. |
Consuming the stream from an HTTP endpoint:

```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api")
public class ActivityHttpEndpoint {

  public record Request(String sessionId, String question) {}

  private final ComponentClient componentClient;

  public ActivityHttpEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/ask")
  public HttpResponse ask(Request request) {
    var responseStream = componentClient
      .forAgent()
      .inSession(request.sessionId)
      .tokenStream(StreamingActivityAgent::query) // (1)
      .source(request.question); // (2)

    return HttpResponses.streamText(responseStream); // (3)
  }


}
```

| **1** | Use `tokenStream` of the component client, instead of `method`, |
| **2** | and invoke it with `source` to receive a stream of tokens. |
| **3** | Return the stream of tokens as a streaming HTTP response. |
The returned stream is a `Source<String, NotUsed>`, i.e. the tokens are always text strings.

The granularity of a token varies by AI model, often representing a word or a short sequence of characters. To reduce the overhead of sending each token as a separate SSE, you can group multiple tokens together using the Akka streams `groupWithin` operator.

```java
@Post("/ask-grouped")
public HttpResponse askGrouped(Request request) {
  var tokenStream = componentClient
    .forAgent()
    .inSession(request.sessionId)
    .tokenStream(StreamingActivityAgent::query)
    .source(request.question);

  var groupedTokenStream = tokenStream
    .groupedWithin(20, Duration.ofMillis// (100)) // (1)
    .map(group -> String.join("", group)); // (2)

  return HttpResponses.streamText(groupedTokenStream); // (3)
}
```

| **1** | Group at most 20 tokens or within 100 milliseconds, whatever happens first. |
| **2** | Concatenate the list of string into a single string. |
| **3** | Return the stream of grouped tokens as a streaming HTTP response. |

## <a href="about:blank#_streaming_from_the_workflow"></a> Streaming from the Workflow

When a workflow orchestrates agent calls, you can bridge the agent’s token stream to the workflow’s notification system. This allows clients to receive real-time LLM responses while still benefiting from workflow orchestration.

```java
@Component(id = "activity")
public class ActivityWorkflow extends Workflow<ActivityWorkflow.State> {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  @JsonSubTypes(
    {
      @JsonSubTypes.Type(value = ActivityWorkflowNotification.StatusUpdate.class, name = "S"),
      @JsonSubTypes.Type(
        value = ActivityWorkflowNotification.LlmResponseStart.class,
        name = "LS"
      ),
      @JsonSubTypes.Type(
        value = ActivityWorkflowNotification.LlmResponseDelta.class,
        name = "LD"
      ),
      @JsonSubTypes.Type(
        value = ActivityWorkflowNotification.LlmResponseEnd.class,
        name = "LE"
      ),
    }
  )
  public sealed interface ActivityWorkflowNotification { // (1)
    record StatusUpdate(String msg) implements ActivityWorkflowNotification {}

    record LlmResponseStart() implements ActivityWorkflowNotification {}

    record LlmResponseDelta(String response) implements ActivityWorkflowNotification {}

    record LlmResponseEnd() implements ActivityWorkflowNotification {}
  }

  private final ComponentClient componentClient;
  private final NotificationPublisher<ActivityWorkflowNotification> notificationPublisher;
  private final Materializer materializer;

  public ActivityWorkflow(
    ComponentClient componentClient,
    NotificationPublisher<ActivityWorkflowNotification> notificationPublisher, // (2)
    Materializer materializer
  ) {
    this.componentClient = componentClient;
    this.notificationPublisher = notificationPublisher;
    this.materializer = materializer;
  }

  @StepName("summarize")
  private StepEffect summarizeStep(String request) {
    var tokenSource = componentClient // (3)
      .forAgent()
      .inSession(sessionId())
      .tokenStream(SummarizerAgent::summarize)
      .source(request);

    notificationPublisher.publish(new ActivityWorkflowNotification.LlmResponseStart()); // (4)

    var finalAnswer = notificationPublisher.publishTokenStream(
      tokenSource, // (5)
      10,
      ofMillis// (200),
      ActivityWorkflowNotification.LlmResponseDelta::new,
      materializer
    );

    notificationPublisher.publish(new ActivityWorkflowNotification.LlmResponseEnd()); // (4)
    notificationPublisher.publish(
      new ActivityWorkflowNotification.StatusUpdate("All steps completed!")
    ); // (4)

    return stepEffects()
      .updateState(currentState().withAnswer(finalAnswer)) // (6)
      .thenPause();
  }

}
```

| **1** | Define notification types using a sealed interface. `LlmResponseStart` and `LlmResponseEnd` signal the streaming lifecycle, `LlmResponseDelta` carries token chunks, and `StatusUpdate` is for workflow progress updates. |
| **2** | Inject `NotificationPublisher` typed with the notification interface. |
| **3** | Get the token source from the agent. |
| **4** | Publish lifecycle notifications to signal when streaming starts and ends. |
| **5** | Use `publishTokenStream` to bridge the token source to notifications. Define parameters to reduce the overhead of sending each token as a separate notification. |
| **6** | Persist the final answer from the agent in the workflow state. |
Clients subscribe to these notifications as described in [Subscribing to notifications](../workflows.html#_subscribing_to_notifications). The client can handle each notification type appropriately—initializing the UI on `LlmResponseStart`, appending text on `LlmResponseDelta`, and finalizing on `LlmResponseEnd`.

<!-- <footer> -->
<!-- <nav> -->
[Extending with function tools](extending.html) [Orchestrating multiple agents](orchestrating.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->