package demo.publishing.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "publishing-agent",
  description = "Publishes approved blog posts, assigning a URL and timestamp"
)
public class PublishingAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(PublishingTasks.PUBLISH).maxIterationsPerTask(3));
  }
}
