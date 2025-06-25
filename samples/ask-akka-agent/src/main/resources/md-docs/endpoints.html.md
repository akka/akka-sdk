<!-- <nav> -->
- [Akka](../../index.html)
- [AI RAG agent part 4: Adding UI endpoints](endpoints.html)

<!-- </nav> -->

# AI RAG agent part 4: Adding UI endpoints

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

In this step of the guide, you’ll add some endpoints to provide a client-friendly API in front of all of the RAG components you’ve been building. You’ll create an API for submitting your "Ask Akka" questions (prompts), and one that serves up a self-hosted, static asset web UI.

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)
You will need to have your MongoDB Atlas database URL and your Open AI API key available, as they are required to run the Ask Akka service.

If you are following along with each step rather than using the completed solution, then you’ll need the code you wrote in the previous step.

## <a href="about:blank#_unfamiliar_with_concepts_like_vectors_embeddings_or_rag"></a> Unfamiliar with concepts like vectors, embeddings or RAG?

We recommend reviewing our [foundational explainer on AI concepts](../../concepts/ai-concepts-video.html). It offers helpful background that will deepen your understanding of the technologies and patterns used throughout this tutorial.

## <a href="about:blank#_add_a_session_history_view"></a> Add a session history view

You probably noticed that in the endpoint and in the agent, we’re tracking both session IDs and user IDs. If you’ve ever used the ChatGPT web interface, then you’re familiar with the layout where a user’s conversation history is shown on the left and you can click on each to view and continue that conversation.

Communication with an LLM is *stateless*. Everything that you get back from a model like ChatGPT is directly related to the prompt you submit. The Agent component in Akka has a built-in session memory, which enables agents to maintain context across multiple interactions. When an agent interacts with an AI model, both the user message and the AI response are automatically stored in the session memory. These messages are then included as additional context in subsequent requests to the model, allowing it to reference previous parts of the interaction.

For the user interface we need a way to pull a conversation history for a given user. We can do this with a view that is built from the events of the built-in `SessionMemoryEntity`.

Add a new file `ConversationHistoryView.java` to `src/main/java/akka/ask/agent/application/`

[ConversationHistoryView.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/application/ConversationHistoryView.java)
```java
@ComponentId("view_chat_log")
public class ConversationHistoryView extends View {

  public record ConversationHistory(List<Session> sessions) {
  }

  public record Message(String message,
      String origin, long timestamp) { // (1)
  }

  public record Session(String userId,
      String sessionId, long creationDate, List<Message> messages) {
    public Session add(Message message) {
      messages.add(message);
      return this;
    }
  }

  @Query("SELECT collect(*) as sessions FROM view_chat_log " +
      "WHERE userId = :userId ORDER by creationDate DESC")
  public QueryEffect<ConversationHistory> getSessionsByUser(String userId) { // (2)
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
  public static class ChatMessageUpdater extends TableUpdater<Session> {

    public Effect<Session> onEvent(SessionMemoryEntity.Event event) {
      return switch (event) {
        case SessionMemoryEntity.Event.AiMessageAdded added -> aiMessage(added);
        case SessionMemoryEntity.Event.UserMessageAdded added -> userMessage(added);
        default -> effects().ignore();
      };
    }

    private Effect<Session> aiMessage(SessionMemoryEntity.Event.AiMessageAdded added) {
      Message newMessage = new Message(added.message(), "ai", added.timestamp().toEpochMilli());
      var rowState = rowStateOrNew(userId(), sessionId());
      return effects().updateRow(rowState.add(newMessage));
    }

    private Effect<Session> userMessage(SessionMemoryEntity.Event.UserMessageAdded added) {
      Message newMessage = new Message(added.message(), "user", added.timestamp().toEpochMilli());
      var rowState = rowStateOrNew(userId(), sessionId());
      return effects().updateRow(rowState.add(newMessage));
    }

    private String userId() {
      var agentSessionId = updateContext().eventSubject().get();
      int i = agentSessionId.indexOf("-");
      return agentSessionId.substring(0, i);
    }

    private String sessionId() {
      var agentSessionId = updateContext().eventSubject().get();
      int i = agentSessionId.indexOf("-");
      return agentSessionId.substring(i+1);
    }

    private Session rowStateOrNew(String userId, String sessionId) { // (3)
      if (rowState() != null)
        return rowState();
      else
        return new Session(
            userId,
            sessionId,
            Instant.now().toEpochMilli(),
            new ArrayList<>());
    }
  }
}
```

| **1** | We’re using a view-specific message type here to avoid bleeding logic across tiers |
| **2** | Retrieves a full history of all sessions for a given user |
| **3** | Convenience method to either get the current row state or make a new one |

## <a href="about:blank#_adding_the_users_api"></a> Adding the users API

There is a convenience endpoint that you can use to query the list of sessions for a given user:

[UsersEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/api/UsersEndpoint.java)
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api")
public class UsersEndpoint {

  private final ComponentClient componentClient;

  public UsersEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/users/{userId}/sessions/")
  public ConversationHistoryView.ConversationHistory getSession(String userId) {

    return componentClient.forView()
        .method(ConversationHistoryView::getSessionsByUser)
        .invoke(userId);
  }
}
```
One subtle thing worth pointing out here is that both the streaming RAG endpoint and the user view query endpoint have the exact same route as defined in `@HttpEndpoint("/api")`.

## <a href="about:blank#_adding_the_static_ui_endpoint"></a> Adding the static UI endpoint

You can now add an endpoint that serves up the static UI. This is surprisingly simple in Akka, as the HTTP endpoint class has built-in support for serving these kinds of assets.

[UiEndpoint.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/api/UiEndpoint.java)
```java
@HttpEndpoint
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class UiEndpoint {
  @Get("/")
  public HttpResponse index() {
    return HttpResponses.staticResource("index.html"); // (1)
  }
}
```

| **1** | The `staticResource` function serves up a file from `main/resources/static-resources` |
And lastly, we just need to fill out the [index.html](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/resources/static-resources/index.html) file to provide the static UI for Ask Akka.

There is far too much code in the HTML file to list out here. If you want to run the UI with the Ask Akka service, here you might want to switch to the version that is in the repository so you can get all of the single-file React code.

## <a href="about:blank#_running_the_service"></a> Running the service

Running the service should now just be a matter of running `mvn compile exec:java`. Make sure that you have set both the `OPENAI_API_KEY` and `MONGODB_ATLAS_URI` environment variables before running `exec:java`.

If you haven’t run the indexer yet, do so with:

```command
curl -XPOST localhost:9000/api/index/start
```
Once you’ve made sure that your MongoDB Atlas database has a functioning and properly named vector index, you can open the Ask Akka UI in the browser: [localhost:9000](http://localhost:9000/).

## <a href="about:blank#_next_steps"></a> Next steps

Now that you’ve gone through the process of building the Ask Akka sample, you should start playing with it and even breaking it. Change the indexing parameters like chunk size and see if that affects how the LLM performs. The key is to roll up your sleeves and get dirty, as that’s the best way to extend your learning beyond what’s covered in this guide.

Make sure you check out our thorough discussion of [agentic AI](https://akka.io/what-is-agentic-ai) and where Akka fits in the ecosystem.

<!-- <footer> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->