package demo.helloworld.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import akka.http.javadsl.model.HttpResponse;
import demo.helloworld.application.QuestionAnswerer;
import demo.helloworld.application.QuestionTasks;
import demo.helloworld.domain.Answer;

import java.util.UUID;

@HttpEndpoint("/questions")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class QuestionEndpoint {

  public record QuestionRequest(String question) {}
  public record QuestionResponse(String taskId) {}
  public record AnswerResponse(String answer, Integer confidence, String status) {}

  private final ComponentClient componentClient;

  public QuestionEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public HttpResponse submitQuestion(QuestionRequest request) {
    if (request.question() == null || request.question().isBlank()) {
      throw HttpException.badRequest("Question must not be empty");
    }

    var taskId = componentClient
        .forAutonomousAgent(QuestionAnswerer.class, UUID.randomUUID().toString())
        .runSingleTask(QuestionTasks.ANSWER.instructions(request.question()));

    return HttpResponses.created(new QuestionResponse(taskId));
  }

  @Get("/{taskId}")
  public AnswerResponse getAnswer(String taskId) {
    var snapshot = componentClient.forTask(QuestionTasks.ANSWER).get(taskId);
    Answer result = snapshot.result();

    return new AnswerResponse(
        result != null ? result.answer() : null,
        result != null ? result.confidence() : null,
        snapshot.status().name());
  }
}
