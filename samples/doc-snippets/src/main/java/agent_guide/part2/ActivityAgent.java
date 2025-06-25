package agent_guide.part2;

// tag::class[]
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.stream.Collectors;

@ComponentId("activity-agent")
public class ActivityAgent extends Agent {

  public record Request(String userId, String message) {}

  private static final String SYSTEM_MESSAGE =
      """
      You are an activity agent. Your job is to suggest activities in the
      real world. Like for example, a team building activity, sports, an
      indoor or outdoor game, board games, a city trip, etc.
      """.stripIndent();

  private final ComponentClient componentClient;

  public ActivityAgent(ComponentClient componentClient) { // <1>
    this.componentClient = componentClient;
  }

  public Effect<String> query(Request request) { // <2>
    var allPreferences =
      componentClient
          .forEventSourcedEntity(request.userId())
          .method(PreferencesEntity::getPreferences)
          .invoke(); // <3>

    String userMessage;
    if (allPreferences.preferences().isEmpty()) {
      userMessage = request.message();
    } else {
      userMessage = request.message() +
          "\nPreferences:\n" +
          allPreferences.preferences().stream()
              .collect(Collectors.joining("'\n", "- ", ""));
    }

    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(userMessage)// <4>
        .thenReply();
  }
}
// end::class[]
