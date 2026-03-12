package demo.helloworld.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.Strategy;
import akka.javasdk.annotations.Component;

@Component(id = "question-answerer")
public class QuestionAnswerer extends AutonomousAgent {

  @Override
  public Strategy strategy() {
    return Strategy.autonomous()
        .goal("Answer questions clearly and concisely, showing reasoning step by step. " +
            "Provide a confidence score from 0 to 100.")
        .accepts(QuestionTasks.ANSWER)
        .maxIterations(3);
  }
}
