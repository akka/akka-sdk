package demo.helloworld.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;
import demo.helloworld.application.QuestionTasks;

@Component(id = "question-answerer")
public class QuestionAnswerer extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .accepts(QuestionTasks.ANSWER)
      .instructions(
        "You are a helpful assistant. Answer the given question clearly and concisely."
      )
      .maxIterations(3);
  }
}
