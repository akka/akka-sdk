package demo.debate.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Moderation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "debate-moderator",
  description = "Moderates structured debates and synthesizes balanced conclusions"
)
public class DebateModerator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        "Moderate structured debates between participants and synthesize balanced conclusions."
      )
      .capability(TaskAcceptance.of(DebateTasks.DEBATE))
      .capability(Moderation.of(Advocate.class, Critic.class).maxRounds(5));
  }
}
