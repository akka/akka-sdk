package demo.publishing.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "publishing-agent")
public class PublishingAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal(
        "Publish approved blog posts. Generate a URL and timestamp for the published post."
      )
      .capability(TaskAcceptance.of(PublishingTasks.PUBLISH).maxIterationsPerTask(3));
  }
}
