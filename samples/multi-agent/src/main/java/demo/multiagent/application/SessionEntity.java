package demo.multiagent.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import demo.multiagent.domain.SessionEvent;
import demo.multiagent.domain.SessionState;
import demo.multiagent.domain.SessionMessage;

import java.time.Instant;
import java.util.List;

import static demo.multiagent.domain.SessionMessage.MessageType.AI;
import static demo.multiagent.domain.SessionMessage.MessageType.USER;

@ComponentId("chat-memory")
public class SessionEntity extends EventSourcedEntity<SessionState, SessionEvent> {

  public record ChatExchange(String query, String response) {
  }


  @Override
  public SessionState emptyState() {
    return SessionState.empty();
  }

  public Effect<Done> addExchange(ChatExchange chatExchange) {

    var now = Instant.now();
    var query = new SessionEvent.UserMessageAdded(
      chatExchange.query,
      now
    );

    var response = new SessionEvent.AiMessageAdded(
      chatExchange.response,
      now
    );

    return effects()
      .persist(query, response)
      .thenReply(__ -> Done.getInstance());
  }

  public Effect<List<SessionMessage>> getHistory() {
    return effects().reply(currentState().messages());
  }

  @Override
  public SessionState applyEvent(SessionEvent event) {
    return switch (event) {
      case SessionEvent.UserMessageAdded msg -> currentState()
        .add(new SessionMessage(msg.query(), USER));

      case SessionEvent.AiMessageAdded msg -> currentState()
        .add(new SessionMessage(msg.response(), AI));
    };
  }
}
