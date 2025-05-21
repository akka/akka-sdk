package akka.ask.agent.api;

import akka.ask.agent.application.AskAkkaAgent;
import akka.ask.agent.application.NewAskAkkaAgent;
import akka.ask.agent.application.StreamedResponse;
import akka.ask.agent.application.StructuredAskAkkaAgent;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.stream.Materializer;

import java.util.Optional;

// tag::endpoint[]
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/api")
public class AskHttpEndpoint {

  public record QueryRequest(String userId, String sessionId, String question) {
  }

  private final ComponentClient componentClient;
  private final AskAkkaAgent askAkkaAgent; // <1>
  private final Materializer materializer;

  public AskHttpEndpoint(AskAkkaAgent askAkkaAgent, Materializer materializer, ComponentClient componentClient) {
    this.askAkkaAgent = askAkkaAgent;
    this.materializer = materializer;
    this.componentClient = componentClient;
  }

  /**
   * This method runs the search and streams the response to the UI.
   */
  @Post("/ask")
  public HttpResponse ask(QueryRequest request) {

    var response = askAkkaAgent
        .ask(request.userId, request.sessionId, request.question)
        .map(StreamedResponse::content); // <2>

    return HttpResponses.serverSentEvents(response); // <3>
  }

  @Post("/new-ask") // FIXME
  public HttpResponse newAsk(QueryRequest request) {
    var responseStream = componentClient
        .forAgent()
        .inSession(request.sessionId())
        .tokenStream(NewAskAkkaAgent::ask)
        .source(request.question);

    return HttpResponses.serverSentEvents(responseStream);
  }

  @Post("/new-ask-structured") // FIXME
  public StructuredAskAkkaAgent.Response newAskStructured(QueryRequest request) {
    return componentClient
        .forAgent()
        .inSession(request.sessionId())
        .method(StructuredAskAkkaAgent::ask)
        .invoke(request.question);
  }
}
// end::endpoint[]
