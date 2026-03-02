package demo.helloworld.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.helloworld.application.QuestionAnswerer;
import demo.helloworld.application.QuestionTasks;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/questions")
public class QuestionEndpoint {

  public record AskQuestion(String question) {}

  public record QuestionResponse(String id) {}

  public record AnswerResponse(String status, Object answer) {}

  private final ComponentClient componentClient;

  public QuestionEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public QuestionResponse ask(AskQuestion request) {
    var ref = componentClient
      .forAutonomousAgent(QuestionAnswerer.class)
      .runSingleTask(QuestionTasks.ANSWER.instructions(request.question()));

    return new QuestionResponse(ref.taskId());
  }

  @Get("/{id}")
  public AnswerResponse get(String id) {
    var task = componentClient.forTask(QuestionTasks.ANSWER.ref(id)).get();
    return new AnswerResponse(task.status().name(), task.result());
  }
}
