package demo.helloworld.application;

import akka.javasdk.agent.task.Task;
import demo.helloworld.domain.Answer;

public class QuestionTasks {


  public static final Task<Answer> ANSWER = Task
      .define("Answer")
      .description("Answer a question clearly and concisely, providing a confidence score")
      .resultConformsTo(Answer.class);
}
