package demo.editorial.application;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/** Team member — writes content sections. Claims tasks from the shared task list. */
@Component(id = "writer")
public class Writer extends AutonomousAgent {

  @Override
  public Strategy configure() {
    return strategy()
      .instructions(
        """
        You are a writer on an editorial team. Check the task list for writing \
        assignments, claim one, research the topic, and write a polished section. \
        Complete the task with your finished content. Work on one task at a time.\
        """
      )
      .tools(EditorialTools.class)
      .maxIterations(10);
  }
}
