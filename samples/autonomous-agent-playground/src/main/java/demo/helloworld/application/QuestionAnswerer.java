package demo.helloworld.application;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

// tag::class[]
@Component( // <2>
  id = "question-answerer",
  description = "Answers questions clearly and concisely, showing reasoning step by step"
)
public class QuestionAnswerer extends AutonomousAgent { // <1>

  @Override
  public AgentDefinition definition() { // <3>
    return define()
      .capability(TaskAcceptance.of(QuestionTasks.ANSWER).maxIterationsPerTask(3));
  }
}
// end::class[]
