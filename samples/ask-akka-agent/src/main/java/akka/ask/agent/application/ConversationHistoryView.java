
package akka.ask.agent.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import akka.javasdk.agent.ConversationMemory;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

// tag::top[]
@ComponentId("view_chat_log")
public class ConversationHistoryView extends View {

  public record ConversationHistory(List<Session> sessions) {
  }

  public record Message(String message,
      String origin, long timestamp) { // <1>
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
  public QueryEffect<ConversationHistory> getSessionsByUser(String userId) { // <2>
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(ConversationMemory.class)
  public static class ChatMessageUpdater extends TableUpdater<Session> {

    public Effect<Session> onEvent(ConversationMemory.Event event) {
      return switch (event) {
        case ConversationMemory.Event.AiMessageAdded added -> aiMessage(added);
        case ConversationMemory.Event.UserMessageAdded added -> userMessage(added);
        default -> effects().ignore();
      };
    }

    private Effect<Session> aiMessage(ConversationMemory.Event.AiMessageAdded added) {
      var timestamp = System.currentTimeMillis(); // FIXME add timestamp to ConversationMemory.Event
      Message newMessage = new Message(added.message(), "ai", timestamp);
      var rowState = rowStateOrNew(userId(), sessionId());
      return effects().updateRow(rowState.add(newMessage));
    }

    private Effect<Session> userMessage(ConversationMemory.Event.UserMessageAdded added) {
      var timestamp = System.currentTimeMillis(); // FIXME add timestamp to ConversationMemory.Event
      Message newMessage = new Message(added.message(), "user", timestamp);
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

    private Session rowStateOrNew(String userId, String sessionId) { // <3>
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
// end::top[]
