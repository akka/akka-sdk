package demo.publishing.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
  id = "content-agent",
  description = "Drafts clear, engaging blog posts on a given topic"
)
public class ContentAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .capability(TaskAcceptance.of(PublishingTasks.DRAFT).maxIterationsPerTask(3));
  }
}
