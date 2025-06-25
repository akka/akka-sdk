package demo.multiagent.application.agents;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import demo.multiagent.domain.AgentResponse;

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

public Effect<AgentResponse> query(String message) {
  return effects()
      .systemMessage(SYSTEM_MESSAGE)
      .userMessage(message)
      .responseAs(AgentResponse.class)
      .thenReply();
}



}
