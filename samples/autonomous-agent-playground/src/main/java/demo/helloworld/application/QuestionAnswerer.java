package demo.helloworld.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "question-answerer")
public class QuestionAnswerer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .goal("Answer questions clearly and concisely, showing reasoning step by step.")
      .canAcceptTask(QuestionTasks.ANSWER, task -> task
        .maxIterationsPerTask(3));
  }
}
