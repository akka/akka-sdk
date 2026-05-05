package demo.helloworld.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import demo.helloworld.application.Answer;
import demo.helloworld.application.QuestionAnswerer;
import demo.helloworld.application.QuestionTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/questions")
public class QuestionEndpoint extends AbstractHttpEndpoint {

  public record AskQuestion(String question) {}

  public record QuestionResponse(String id, String runId, String agentComponentId) {}

  private final ComponentClient componentClient;

  public QuestionEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public QuestionResponse ask(AskQuestion request) {
    var agentInstanceId = requestContext().queryParams().getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    // tag::run-single-task[]
    var taskId = componentClient
      .forAutonomousAgent(QuestionAnswerer.class, agentInstanceId)
      .runSingleTask(QuestionTasks.ANSWER.instructions(request.question()));
    // end::run-single-task[]
    return new QuestionResponse(taskId, agentInstanceId, "question-answerer");
  }

  @Get("/{taskId}")
  public Answer getAnswer(String taskId) {
    return componentClient.forTask(taskId).get(QuestionTasks.ANSWER).result();
  }
}
