
package akka.ask.agent.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import akka.javasdk.agent.SessionMemoryEntity;
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
