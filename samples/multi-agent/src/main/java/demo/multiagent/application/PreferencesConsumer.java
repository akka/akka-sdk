package demo.multiagent.application;

// tag::all[]
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import demo.multiagent.domain.PreferencesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("preferences-consumer")
@Consume.FromEventSourcedEntity(PreferencesEntity.class) // <1>
public class PreferencesConsumer extends Consumer { // <2>

  private static final Logger logger = LoggerFactory.getLogger(PreferencesConsumer.class);

  private final ComponentClient componentClient;

  public PreferencesConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onPreferenceAdded(PreferencesEvent.PreferenceAdded event) {
    var userId = messageContext().eventSubject().get(); // the entity id
    logger.info("Preference added for user {}: {}", userId, event.preference());

    // Get all activities (sessions) for this user from the ActivityView
    var activities = componentClient
        .forView()
        .method(ActivityView::getActivities)
        .invoke(userId); // <3>

    // Call EvaluatorAgent for each session
    for (var activity : activities.entries()) {
      if (activity.finalAnswer() != null && !activity.finalAnswer().isEmpty()) {
        var evaluationRequest = new EvaluatorAgent.EvaluationRequest(
            userId,
            activity.userQuestion(),
            activity.finalAnswer()
        );

        var evaluationResult = componentClient
            .forAgent()
            .inSession(activity.sessionId())
            .method(EvaluatorAgent::evaluate)
            .invoke(evaluationRequest); // <4>

        logger.info("Evaluation completed for session {}: score={}, feedback='{}'",
            activity.sessionId(),
            evaluationResult.score(),
            evaluationResult.feedback());

        if (evaluationResult.score() < 3) {
          // run the workflow again to generate a better answer

          componentClient
              .forWorkflow(activity.sessionId())
              .method(AgentTeamWorkflow::runAgain) // <5>
              .invoke();

          logger.info("Started workflow {} for user {} to re-answer question: '{}'",
              activity.sessionId(), userId, activity.userQuestion());
        }

      }
    }

    return effects().done();
  }
}
// end::all[]
