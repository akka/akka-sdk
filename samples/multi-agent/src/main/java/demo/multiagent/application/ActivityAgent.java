package demo.multiagent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import demo.multiagent.domain.AgentRequest;
import demo.multiagent.domain.AgentResponse;

import java.util.stream.Collectors;

// tag::description[]
@ComponentId("activity-agent")
@AgentDescription(
  name = "Activity Agent",
  description = """
      An agent that suggests activities in the real world. Like for example,
      a team building activity, sports, an indoor or outdoor game, board games, a city trip, etc.
    """,
    role = "worker"
)
public class ActivityAgent extends Agent {
// end::description[]

  // tag::system_message[]
  private static final String SYSTEM_MESSAGE = ("""
      You are an activity agent. Your job is to suggest activities in the real world.
      Like for example, a team building activity, sports, an indoor or outdoor game,
      board games, a city trip, etc.
    """.stripIndent() + AgentResponse.FORMAT_INSTRUCTIONS);
  // end::system_message[]

  private final ComponentClient componentClient;

  public ActivityAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

public Effect<AgentResponse> query(AgentRequest request) {
  var allPreferences =
      componentClient
          .forEventSourcedEntity(request.userId())
          .method(PreferencesEntity::getPreferences)
          .invoke();

  var userMessage = request.message() +
      "\nPreferences:\n" +
      allPreferences.preferences().stream()
          .collect(Collectors.joining("'\n", "- ", ""));

  return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(userMessage)
      .responseAs(AgentResponse.class)
      .thenReply();
}



}
