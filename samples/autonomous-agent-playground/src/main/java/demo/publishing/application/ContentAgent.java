package demo.publishing.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "content-agent")
public class ContentAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal("Draft blog posts. Write clear, engaging content on the given topic.")
      .capability(TaskAcceptance.of(PublishingTasks.DRAFT).maxIterationsPerTask(3));
  }
}
