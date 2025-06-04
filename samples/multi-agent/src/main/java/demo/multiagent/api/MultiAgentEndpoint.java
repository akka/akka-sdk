package demo.multiagent.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import demo.multiagent.application.AgenticWorkflow;

import java.util.UUID;


@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/chat")
public class MultiAgentEndpoint {

  public record Request(String message) {
  }

  private final ComponentClient componentClient;

  public MultiAgentEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post()
  public HttpResponse handleRequest( Request request) {
    var chatId = UUID.randomUUID().toString();
    var res =
      componentClient
      .forWorkflow(chatId)
        .method(AgenticWorkflow::start)
        .invoke(request.message);

    return HttpResponses.created(res, "/chat/" + chatId);
  }

  @Get("/{chatId}")
  public HttpResponse getAnswer(String chatId) {
      var res =
        componentClient
          .forWorkflow(chatId)
          .method(AgenticWorkflow::getAnswer)
          .invoke();

      if (res.isEmpty())
        return HttpResponses.notFound("Answer for '" + chatId + "' not available (yet)");
      else
        return HttpResponses.ok(res);
  }
}
