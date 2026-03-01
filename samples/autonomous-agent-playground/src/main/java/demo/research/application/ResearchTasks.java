package demo.research.application;

import akka.javasdk.agent.task.Task;

public class ResearchTasks {

  public static final Task<ResearchBrief> BRIEF = Task.of(
    "Produce a research brief",
    ResearchBrief.class
  );
}
