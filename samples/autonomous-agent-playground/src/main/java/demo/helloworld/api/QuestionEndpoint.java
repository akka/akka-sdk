package demo.helloworld.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.helloworld.application.Answer;
import demo.helloworld.application.QuestionAnswerer;
import demo.helloworld.application.QuestionTasks;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/questions")
public class QuestionEndpoint {

  public record AskQuestion(String question) {}

  public record QuestionResponse(String id, String runId, String agentComponentId) {}

  private final ComponentClient componentClient;

  public QuestionEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public QuestionResponse ask(AskQuestion request) {
    var agentInstanceId = UUID.randomUUID().toString();
    var taskId = componentClient
      .forAutonomousAgent(QuestionAnswerer.class, agentInstanceId)
      .runSingleTask(QuestionTasks.ANSWER.instructions(request.question()));
    return new QuestionResponse(taskId, agentInstanceId, "question-answerer");
  }

  @Get("/{taskId}")
  public Answer getAnswer(String taskId) {
    return componentClient.forTask(taskId).get(QuestionTasks.ANSWER).result();
  }
}
