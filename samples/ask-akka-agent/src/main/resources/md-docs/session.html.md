

<-nav->

- [  Akka](../../index.html)
- [  Developing](../index.html)
- [  Samples](../samples.html)
- [  AI RAG Agent](index.html)
- [  Modeling the agent session](session.html)



</-nav->



# Modeling the agent session

Communication with an LLM is *stateless* . Everything that you get back from a model like ChatGPT is directly related to the prompt you submit. If you want to maintain a conversation and its history, then you have to do that work on your own in your agent code. With a few exceptions, the hosted  LLMs won’t maintain this history for you.

Before we get into the details of how to talk to an LLM, how to do document indexing, how to generate prompts, and many other details, let’s get started with something simple like building a session (e.g. conversation) entity.

## [](about:blank#_overview) Overview

This guide is concerned just with the session entity. We will get to more AI-specific tasks in the next steps.

In this guide you will:

- Create a new, empty Akka project
- Create a data type for session events
- Create a session entity
- Create a conversation history view

## [](about:blank#_prerequisites) Prerequisites

- Java 21, we recommend[  Eclipse Adoptium](https://adoptium.net/marketplace/)
- [  Apache Maven](https://maven.apache.org/install.html)   version 3.9 or later
- <a href="https://curl.se/download.html"> `curl`   command-line tool</a>
- An[  Akka account](https://console.akka.io/register)
- [  Docker Engine](https://docs.docker.com/get-started/get-docker/)   27 or later

## [](about:blank#_create_the_empty_project) Create the empty project

You already learned how to create an empty Akka project when you went through the guide to [author your first service](../author-your-first-service.html#_generate_and_build_the_project) . Follow those steps again to ensure that you’ve got a new project that compiles.

While you can use any settings you like, the code samples use:

- Group ID:**  akka.ask**
- Artifact ID:**  ask-akka**
- Version:**  1.0-SNAPSHOT**

|  | This guide is written assuming you will follow it as a tutorial to walk through all of the components, building them on your own. If at any time you want to compare your solution with the official sample, check out the[  Github repository](https://github.com/akka-samples/ask-akka-agent)  . |

## [](about:blank#_create_the_session_event) Create the session event

Conversations, or sessions, with an LLM can be modeled nearly the same way as you might model the data from a regular chat application. You want to have the list of messages and, for each message, you want to know who supplied the message and when it was written. For LLM conversations, we also want to keep track of **token** usage because that may tie directly to how much a given session costs in real money.

There are two types of participants in an LLM conversation:

- The user
- The AI (LLM)

This means we can model a session event with `UserMessageAdded` and `AiMessageAdded` variants. While they have nearly identical structures now, it’s worth keeping them as separate variants to allow them to expand separately in the future.

Add a `SessionEvent.java` file to your `src/main/java/akka/ask/agent/domain/` directory (we’re using the `agent` folder here because we cheated and know that we’ll be creating other folders like `common` and `indexer` in later guides):

[SessionEvent.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/domain/SessionEvent.java)
```java
package akka.ask.agent.domain;

import java.time.Instant;
import akka.javasdk.annotations.TypeName;

public sealed interface SessionEvent {

  @TypeName("user-message-added")
  public record UserMessageAdded(
      String userId,
      String sessionId,
      String query,
      int tokensUsed,
      Instant timeStamp)
      implements SessionEvent {
  }

  @TypeName("ai-message-added")
  public record AiMessageAdded(
      String userId,
      String sessionId,
      String response, // (1)
      int tokensUsed,
      Instant timeStamp)
      implements SessionEvent {
  }
}
```

| **  1** | We could have called both fields `message`   but using `query`   and `response`   felt more concise |

Now that we’ve got a session event, let’s build the session entity and state.

## [](about:blank#_build_the_session_entity) Build the session entity

As with all entities, the session entity handles incoming commands, validates them, and emits corresponding events. We’ve decided to have the entity record *exchanges* , which are commands that contain the user-supplied prompt and the response that came back from the LLM, as well as the tokens consumed for each.

Add an entity to `src/main/java/akka/ask/agent/application/SessionEntity.java`

[SessionEntity.java](https://github.com/akka/akka-sdk/blob/main/samples/ask-akka-agent/src/main/java/akka/ask/agent/application/SessionEntity.java)
```java
@ComponentId("session-entity")
public class SessionEntity extends EventSourcedEntity<SessionEntity.State, SessionEvent> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public record Exchange(String userId, // (1)
      String sessionId,
      String userQuestion,
      int queryTokensCount,
      String assistantResponse,
      int responseTokensCount) {
  }

  enum MessageType {
    AI,
    USER
  }

  public record Message(String content, MessageType type) { // (2)
  }

  public record State(List<Message> messages, int totalTokenUsage) {
    public static State empty() {
      return new State(new ArrayList<>(), 0);
    }

    public State add(Message content) {
      messages.add(content);
      return new State(messages, totalTokenUsage);
    }

    public State addTokenUsage(int usage) {
      return new State(messages, totalTokenUsage + usage);
    }
  }

  public record Messages(List<Message> messages) {
  }

  public Effect<Done> addExchange(Exchange exchange) {

    var now = Instant.now();

    var userEvt = new SessionEvent.UserMessageAdded(
        exchange.userId,
        exchange.sessionId,
        exchange.userQuestion,
        exchange.queryTokensCount,
        now);

    var assistantEvt = new SessionEvent.AiMessageAdded(
        exchange.userId,
        exchange.sessionId,
        exchange.assistantResponse,
        exchange.responseTokensCount, // (3)
        now);

    return effects()
        .persist(userEvt, assistantEvt) // (4)
        .thenReply(__ -> Done.getInstance());
  }

  public Effect<Messages> getHistory() {
    logger.debug("Getting history from {}", commandContext().entityId());
    return effects().reply(new Messages(currentState().messages));
  }

  @Override
  public State emptyState() {
    return State.empty();
  }

  @Override
  public State applyEvent(SessionEvent event) {
    return switch (event) {
      case SessionEvent.UserMessageAdded msg ->
        currentState()
            .add(new Message(msg.query(), USER))
            .addTokenUsage(msg.tokensUsed());

      case SessionEvent.AiMessageAdded msg ->
        currentState()
            .add(new Message(msg.response(), AI))
            .addTokenUsage(msg.tokensUsed());
    };
  }
}
```

| **  1** | The type of incoming commands is `Exchange` |
| **  2** | *  Internal*   entity state stores messages in the `Message`   record type |
| **  3** | Note that we track input and output token usage separately |
| **  4** | A single exchange is split into a user message event and an AI message event |

Make sure that you understand the mechanics of what’s happening in this entity and it compiles before continuing. It’s worth appreciating that even though we’re going to use this entity to store conversation histories with an LLM, there’s no LLM code in here because entities don’t perform work.

## [](about:blank#_add_a_session_history_view) Add a session history view

You probably noticed that in the session events and the entity state, we’re tracking both session IDs and user IDs. If you’ve ever used the ChatGPT web interface, then you’re familiar with the layout where a user’s conversation history is shown on the left and you can click on each to view and continue that conversation.

This is exactly how we’re going to model our "Ask Akka" application. As such, we’re going to need a view that gives us a friendly data structure for conversations as well as lets us pull a conversation history for a given user.

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

  @Consume.FromEventSourcedEntity(SessionEntity.class)
  public static class ChatMessageUpdater extends TableUpdater<Session> {

    public Effect<Session> onEvent(SessionEvent event) {
      return switch (event) {
        case SessionEvent.AiMessageAdded added -> aiMessage(added);
        case SessionEvent.UserMessageAdded added -> userMessage(added);
      };
    }

    private Effect<Session> aiMessage(SessionEvent.AiMessageAdded added) {
      Message newMessage = new Message(added.response(), "ai", added.timeStamp().toEpochMilli());
      var rowState = rowStateOrNew(added.userId(), added.sessionId());
      return effects().updateRow(rowState.add(newMessage));
    }

    private Effect<Session> userMessage(SessionEvent.UserMessageAdded added) {
      Message newMessage = new Message(added.query(), "user", added.timeStamp().toEpochMilli());
      var rowState = rowStateOrNew(added.userId(), added.sessionId());
      return effects().updateRow(rowState.add(newMessage));
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

| **  1** | We’re using a view-specific message type here to avoid bleeding logic across tiers |
| **  2** | Retrieves a full history of all sessions for a given user |
| **  3** | Convenience method to either get the current row state or make a new one |

## [](about:blank#_next_steps) Next steps

Now would be an ideal time to create [unit tests](../views.html#_testing_the_view) for the view and the entity. Once you’ve done that (or skipped it), it’s time to explore our first aspect of the agentic RAG flow: the indexing **workflow** and the **vector database**.



<-footer->


<-nav->
[AI RAG Agent](index.html) [Knowledge indexing with a workflow](indexer.html)

</-nav->


</-footer->


<-aside->


</-aside->
