package assistant.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import assistant.application.ClaimReviewRequestWorkflow;
import assistant.application.ClaimsAssistantAgent;
import java.util.UUID;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class ClaimsAssistantEndpoint {

  public record Question(String message) {}

  public record ReviewRequest(String policyNumber) {}

  private final ComponentClient componentClient;

  public ClaimsAssistantEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/assistant")
  public String ask(Question question) {
    return componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(ClaimsAssistantAgent::ask)
      .invoke(question.message());
  }

  @Post("/reviews/{claimId}")
  public HttpResponse requestReview(String claimId, ReviewRequest request) {
    componentClient
      .forWorkflow(claimId)
      .method(ClaimReviewRequestWorkflow::start)
      .invoke(new ClaimReviewRequestWorkflow.Request(claimId, request.policyNumber()));
    return HttpResponses.created("/reviews/" + claimId);
  }

  @Get("/reviews/{claimId}")
  public ClaimReviewRequestWorkflow.State getReview(String claimId) {
    return componentClient
      .forWorkflow(claimId)
      .method(ClaimReviewRequestWorkflow::get)
      .invoke();
  }
}
