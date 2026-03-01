package demo.research.application;

import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskTemplate;

public class ResearchTasks {

  public static final TaskTemplate<ResearchBrief> BRIEF = Task.of(
    "Produce a research brief",
    ResearchBrief.class
  ).instructionTemplate("Research {topic}. Focus area: {focus}.");
}
