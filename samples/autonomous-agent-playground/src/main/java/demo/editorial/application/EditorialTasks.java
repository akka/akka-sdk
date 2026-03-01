package demo.editorial.application;

import akka.javasdk.agent.task.Task;

public class EditorialTasks {

  public static final Task<Publication> PUBLICATION = Task.of(
    "Produce a publication",
    Publication.class
  );
}
