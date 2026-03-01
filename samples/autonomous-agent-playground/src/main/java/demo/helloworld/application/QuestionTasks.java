package demo.helloworld.application;

import akka.javasdk.agent.task.Task;

public class QuestionTasks {

  public static final Task<Answer> ANSWER = Task.of("Answer a question", Answer.class);
}
