package demo.helloworld.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "question-answerer")
public class QuestionAnswerer extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
      .purpose("Answer questions.")
      .guidance("Be clear and concise. Show reasoning step by step.")
      .capability(TaskAcceptance.of(QuestionTasks.ANSWER).maxIterationsPerTask(3));
  }
}
