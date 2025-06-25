package akka.ask.agent.api;

import akka.ask.agent.application.AskAkkaAgent;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;

// tag::endpoint[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api")
public class AskHttpEndpoint {

  public record QueryRequest(String userId, String sessionId, String question) {
  }

  private final ComponentClient componentClient;

  public AskHttpEndpoint(ComponentClient componentClient) { // <1>
    this.componentClient = componentClient;
  }

  /**
   * This method runs the search and streams the response to the UI.
   */
  @Post("/ask")
  public HttpResponse ask(QueryRequest request) {
    var sessionId = request.userId() + "-" + request.sessionId();
    var responseStream = componentClient
        .forAgent()
        .inSession(sessionId)
        .tokenStream(AskAkkaAgent::ask)
        .source(request.question); // <2>

    return HttpResponses.serverSentEvents(responseStream); // <3>
  }

}
// end::endpoint[]
