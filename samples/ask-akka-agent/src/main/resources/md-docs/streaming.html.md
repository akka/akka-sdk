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

    return HttpResponses.serverSentEvents(responseStream); // (3)
  }


}
```

| **1** | Use `tokenStream` of the component client, instead of `method`, |
| **2** | and invoke it with `source` to receive a stream of tokens. |
| **3** | Return the stream of tokens as SSE. |
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

  return HttpResponses.serverSentEvents(groupedTokenStream); // (3)
}
```

| **1** | Group at most 20 tokens or within 100 milliseconds, whatever happens first. |
| **2** | Concatenate the list of string into a single string. |
| **3** | Return the stream of grouped tokens as SSE. |

|  | Token streams are designed for direct agent calls from an endpoint. You can’t use a token stream when you have an intermediate workflow between the endpoint and the agent. |

<!-- <footer> -->
<!-- <nav> -->
[Extending with function tools](extending.html) [Orchestrating multiple agents](orchestrating.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->