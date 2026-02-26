package demo.helloworld.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

@Component(id = "question-answerer")
public class QuestionAnswerer extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        "You are a helpful assistant. Answer the given question clearly and concisely."
      )
      .maxIterations(3);
  }
}
