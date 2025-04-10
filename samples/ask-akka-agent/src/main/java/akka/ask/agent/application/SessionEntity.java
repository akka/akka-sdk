package akka.ask.agent.application;

import akka.Done;
import akka.ask.agent.domain.SessionEvent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static akka.ask.agent.application.SessionEntity.MessageType.AI;
import static akka.ask.agent.application.SessionEntity.MessageType.USER;

// tag::top[]
@ComponentId("session-entity")
public class SessionEntity extends EventSourcedEntity<SessionEntity.State, SessionEvent> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public record Exchange(String userId, // <1>
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

  public record Message(String content, MessageType type) { // <2>
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
        exchange.responseTokensCount, // <3>
        now);

    return effects()
        .persist(userEvt, assistantEvt) // <4>
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
// end::top[]
