package demo.helloworld.application;

import akka.javasdk.agent.task.Task;

public class QuestionTasks {

  // prettier-ignore
  public static final Task<Answer> ANSWER = Task
    .define("Answer")
    .description("Answer a question")
    .resultConformsTo(Answer.class);
}
