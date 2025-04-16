
package akka.ask.agent.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import akka.ask.agent.domain.SessionEvent;
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
