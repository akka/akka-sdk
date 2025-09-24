package demo.multiagent.application;

import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMemoryEntity.Event.AiMessageAdded;
import akka.javasdk.agent.SessionMemoryEntity.Event.UserMessageAdded;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ComponentId("session-memory-eval-consumer")
@Consume.FromEventSourcedEntity(value = SessionMemoryEntity.class, ignoreUnknown = true)
public class SessionMemoryConsumer extends Consumer {

  private static final Logger logger = LoggerFactory.getLogger(SessionMemoryConsumer.class);

  private final ComponentClient componentClient;

  public SessionMemoryConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onUserMessage(UserMessageAdded event) {

    return effects().done();
  }

  public Effect onAiMessage(AiMessageAdded event) {
    if (event.componentId().equals("summarizer-agent")) {
      evalToxicity(event);
      evalSummarization(event);
    }

    return effects().done();
  }

  private void evalToxicity(AiMessageAdded event) {
    componentClient
        .forAgent()
        .inSession(sessionId())
        .method(ToxicityEvaluator::evaluate)
        .invoke(event.message());
  }

  private void evalSummarization(AiMessageAdded event) {
    var sessionHistory =
        componentClient
            .forEventSourcedEntity(sessionId())
            .method(SessionMemoryEntity::getHistory)
            .invoke(new SessionMemoryEntity.GetHistoryCmd(Optional.empty()));

    Optional<SessionMessage> correspondingUserMessage = sessionHistory.messages().stream()
        .takeWhile(m -> switch (m) {
          case SessionMessage.AiMessage msg when msg.timestamp().equals(event.timestamp()) -> false;
          default -> true;
        })
        .filter(m -> m instanceof SessionMessage.UserMessage)
        .reduce((first, second) -> second);

    if (correspondingUserMessage.isPresent()) {
      componentClient
          .forAgent()
          .inSession(sessionId())
          .method(SummarizationEvaluator::evaluate)
          .invoke(new SummarizationEvaluator.EvaluationRequest(
              correspondingUserMessage.get().text(), event.message()));
    }
  }


  private String sessionId() {
    return messageContext().eventSubject().get();
  }
}

