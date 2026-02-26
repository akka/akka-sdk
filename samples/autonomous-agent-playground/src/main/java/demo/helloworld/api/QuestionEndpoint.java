package demo.helloworld.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.helloworld.application.Answer;
import demo.helloworld.application.QuestionAnswerer;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/questions")
public class QuestionEndpoint {

  public record AskQuestion(String question) {}

  public record QuestionResponse(String id) {}

  public record AnswerResponse(String status, Answer answer) {}

  private final ComponentClient componentClient;

  public QuestionEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public QuestionResponse ask(AskQuestion request) {
    var taskId = componentClient
      .forAutonomousAgent(QuestionAnswerer.class)
      .runSingleTask(request.question(), Answer.class);

    return new QuestionResponse(taskId);
  }

  @Get("/{id}")
  public AnswerResponse get(String id) {
    var task = componentClient.forTask(id, Answer.class);
    var state = task.getState();
    return new AnswerResponse(state.status().name(), task.getResult());
  }
}
