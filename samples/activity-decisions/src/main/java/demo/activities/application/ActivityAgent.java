package demo.activities.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "activity-agent")
public class ActivityAgent extends Agent {

  /**
   * @param weather the weather at the user's location, empty when the workflow did not ask for it
   * @param feedback why the previous suggestion was not accepted, empty on the first attempt
   */
  public record Request(String userId, String message, String weather, String feedback) {}

  private static final Logger logger = LoggerFactory.getLogger(ActivityAgent.class);

  private static final String SYSTEM_MESSAGE =
    """
    You are an activity agent. Your job is to suggest activities in the
    real world. Like for example, a team building activity, sports, an
    indoor or outdoor game, board games, a city trip, etc.
    Respect every preference of the user. Suggest at most three activities.
    """.stripIndent();

  private final ComponentClient componentClient;

  public ActivityAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> query(Request request) {
    logger.info("Invoked for user [{}] with: {}", request.userId(), request.message());
    var preferences = componentClient
      .forEventSourcedEntity(request.userId())
      .method(PreferencesEntity::getPreferences)
      .invoke()
      .entries();

    var userMessage = new StringBuilder(request.message());
    if (!request.weather().isEmpty()) {
      userMessage.append("\n\nWeather: ").append(request.weather());
    }
    if (!preferences.isEmpty()) {
      userMessage
        .append("\n\nPreferences:\n")
        .append(preferences.stream().collect(Collectors.joining("\n- ", "- ", "")));
    }
    if (!request.feedback().isEmpty()) {
      userMessage
        .append("\n\nFeedback on your previous suggestion: ")
        .append(request.feedback());
    }

    return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(userMessage.toString())
      .thenReply();
  }
}
