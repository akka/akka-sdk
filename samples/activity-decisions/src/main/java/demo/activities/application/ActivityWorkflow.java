package demo.activities.application;

import static demo.activities.application.ActivityQuestions.NEEDS_WEATHER;
import static demo.activities.application.ActivityQuestions.QUALITY;
import static demo.activities.application.ActivityQuestions.RESPECTS_PREFERENCES;
import static demo.activities.application.ActivityQuestions.TOPIC;
import static java.time.Duration.ofSeconds;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.decision.Decision;
import akka.javasdk.decision.DecisionModelClient;
import akka.javasdk.decision.Question;
import akka.javasdk.workflow.Workflow;
import akka.javasdk.workflow.Workflow.RecoverStrategy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Suggests activities for a user. The decision model chooses the steps: whether the message is
 * about activities at all, whether the weather matters, and whether the suggestion of the activity
 * agent is good enough or the agent must try again.
 */
@Component(id = "activity-workflow")
public class ActivityWorkflow extends Workflow<ActivityWorkflow.State> {

  private static final Logger logger = LoggerFactory.getLogger(ActivityWorkflow.class);

  // Thresholds for the answers of the decision model. Tune them against real answers.
  private static final double NEEDS_WEATHER_THRESHOLD = 0.5;
  private static final double QUALITY_THRESHOLD = 1.5;
  private static final double PREFERENCES_THRESHOLD = 0.5;
  private static final int MAX_SUGGESTIONS = 2;

  public record Request(String userId, String message) {}

  /** A decision of the decision model, with the step that asked for it. */
  public record StepDecision(String step, Decision decision) {}

  /** The input for the check of a suggestion. The decision model receives it as JSON. */
  public record SuggestionCheck(
    String request,
    List<String> preferences,
    String weather,
    String suggestion
  ) {}

  /** The answer is empty until the workflow ends. */
  public record Result(String answer, String failure, List<StepDecision> decisions) {}

  public record State(
    String userId,
    String message,
    String topic,
    String weather,
    String suggestion,
    String feedback,
    int suggestions,
    String answer,
    String failure,
    List<StepDecision> decisions
  ) {
    static State of(String userId, String message) {
      return new State(userId, message, "", "", "", "", 0, "", "", List.of());
    }

    State withDecision(String step, Decision decision) {
      var all = new ArrayList<>(decisions);
      all.add(new StepDecision(step, decision));
      return new State(
        userId,
        message,
        topic,
        weather,
        suggestion,
        feedback,
        suggestions,
        answer,
        failure,
        all
      );
    }

    State withTopic(String t) {
      return new State(
        userId,
        message,
        t,
        weather,
        suggestion,
        feedback,
        suggestions,
        answer,
        failure,
        decisions
      );
    }

    State withWeather(String w) {
      return new State(
        userId,
        message,
        topic,
        w,
        suggestion,
        feedback,
        suggestions,
        answer,
        failure,
        decisions
      );
    }

    State withSuggestion(String s) {
      return new State(
        userId,
        message,
        topic,
        weather,
        s,
        feedback,
        suggestions + 1,
        answer,
        failure,
        decisions
      );
    }

    State withFeedback(String f) {
      return new State(
        userId,
        message,
        topic,
        weather,
        suggestion,
        f,
        suggestions,
        answer,
        failure,
        decisions
      );
    }

    State withAnswer(String a) {
      return new State(
        userId,
        message,
        topic,
        weather,
        suggestion,
        feedback,
        suggestions,
        a,
        failure,
        decisions
      );
    }

    State withFailure(String f) {
      return new State(
        userId,
        message,
        topic,
        weather,
        suggestion,
        feedback,
        suggestions,
        answer,
        f,
        decisions
      );
    }
  }

  private final ComponentClient componentClient;
  private final DecisionModelClient decisionModelClient;

  public ActivityWorkflow(
    ComponentClient componentClient,
    DecisionModelClient decisionModelClient
  ) {
    this.componentClient = componentClient;
    this.decisionModelClient = decisionModelClient;
  }

  public Effect<Done> start(Request request) {
    if (currentState() != null) {
      return effects()
        .error("Workflow '" + commandContext().workflowId() + "' already started");
    }
    return effects()
      .updateState(State.of(request.userId(), request.message()))
      .transitionTo(ActivityWorkflow::classify)
      .thenReply(Done.getInstance());
  }

  public Effect<Result> getResult() {
    if (currentState() == null) {
      return effects().error("Workflow '" + commandContext().workflowId() + "' not started");
    }
    var state = currentState();
    return effects().reply(new Result(state.answer(), state.failure(), state.decisions()));
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
      // a decision model call retries a failed request, so give it time
      .defaultStepTimeout(ofSeconds(90))
      .defaultStepRecovery(RecoverStrategy.maxRetries(1).failoverTo(ActivityWorkflow::error))
      .build();
  }

  @StepName("classify")
  private StepEffect classify() {
    Decision decision;
    try {
      decision = decisionModelClient
        .input(currentState().message())
        .questions(TOPIC, NEEDS_WEATHER)
        .invoke();
    } catch (RuntimeException e) {
      return stepEffects()
        .updateState(currentState().withFailure(decisionFailure("classify", e)))
        .thenEnd();
    }

    var topic = decision.answer(TOPIC);
    var needsWeather = decision.answer(NEEDS_WEATHER).probability();
    logger.info(
      "Model [{}]: topic [{}] with probabilities {}, needs weather with probability {}",
      decision.model(),
      topic.selected(),
      topic.probabilities(),
      needsWeather
    );

    var state = currentState().withDecision("classify", decision).withTopic(topic.selected());
    return switch (topic.selected()) {
      case "activities" -> needsWeather >= NEEDS_WEATHER_THRESHOLD
        ? stepEffects().updateState(state).thenTransitionTo(ActivityWorkflow::askWeather)
        : stepEffects()
          .updateState(state)
          .thenTransitionTo(ActivityWorkflow::suggestActivities);
      case "weather" -> stepEffects()
        .updateState(state)
        .thenTransitionTo(ActivityWorkflow::askWeather);
      default -> stepEffects()
        .updateState(
          state.withAnswer("I can only suggest activities and tell you the weather.")
        )
        .thenEnd();
    };
  }

  @StepName("weather")
  private StepEffect askWeather() {
    var weather = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(WeatherAgent::query)
      .invoke(currentState().message());
    logger.info("Weather: {}", weather);

    var state = currentState().withWeather(weather);
    if (state.topic().equals("weather")) {
      return stepEffects().updateState(state.withAnswer(weather)).thenEnd();
    }
    return stepEffects()
      .updateState(state)
      .thenTransitionTo(ActivityWorkflow::suggestActivities);
  }

  @StepName("activities")
  private StepEffect suggestActivities() {
    var state = currentState();
    var suggestion = componentClient
      .forAgent()
      .inSession(sessionId())
      .method(ActivityAgent::query)
      .invoke(
        new ActivityAgent.Request(
          state.userId(),
          state.message(),
          state.weather(),
          state.feedback()
        )
      );
    logger.info("Suggestion: {}", suggestion);

    return stepEffects()
      .updateState(state.withSuggestion(suggestion))
      .thenTransitionTo(ActivityWorkflow::checkSuggestion);
  }

  @StepName("check")
  private StepEffect checkSuggestion() {
    var state = currentState();
    var preferences = componentClient
      .forEventSourcedEntity(state.userId())
      .method(PreferencesEntity::getPreferences)
      .invoke()
      .entries();
    List<Question<?>> questions = preferences.isEmpty()
      ? List.of(QUALITY)
      : List.of(QUALITY, RESPECTS_PREFERENCES);

    Decision decision;
    try {
      decision = decisionModelClient
        .input(
          new SuggestionCheck(
            state.message(),
            preferences,
            state.weather(),
            state.suggestion()
          )
        )
        .questions(questions)
        .invoke();
    } catch (RuntimeException e) {
      return stepEffects()
        .updateState(currentState().withFailure(decisionFailure("check", e)))
        .thenEnd();
    }

    var feedback = new ArrayList<String>();
    var quality = decision.answer(QUALITY).value();
    if (quality < QUALITY_THRESHOLD) {
      feedback.add("It does not answer the whole request.");
    }
    var respectsPreferences = preferences.isEmpty()
      ? 1.0
      : decision.answer(RESPECTS_PREFERENCES).probability();
    if (respectsPreferences < PREFERENCES_THRESHOLD) {
      feedback.add("It does not respect every preference.");
    }
    logger.info(
      "Model [{}]: quality {}, respects the preferences with probability {}",
      decision.model(),
      quality,
      respectsPreferences
    );

    var checked = state.withDecision("check", decision);
    if (feedback.isEmpty() || state.suggestions() >= MAX_SUGGESTIONS) {
      return stepEffects().updateState(checked.withAnswer(state.suggestion())).thenEnd();
    }
    return stepEffects()
      .updateState(checked.withFeedback(String.join(" ", feedback)))
      .thenTransitionTo(ActivityWorkflow::suggestActivities);
  }

  private StepEffect error() {
    return stepEffects()
      .updateState(currentState().withFailure("A step failed. See the service log."))
      .thenEnd();
  }

  // The failure ends the workflow, so that the result shows why the decision model failed.
  private static String decisionFailure(String step, RuntimeException e) {
    logger.warn("The decision model call of step [{}] failed", step, e);
    return step + ": " + e.getClass().getSimpleName() + ": " + e.getMessage();
  }

  private String sessionId() {
    // the workflow corresponds to the session of the agents
    return commandContext().workflowId();
  }
}
