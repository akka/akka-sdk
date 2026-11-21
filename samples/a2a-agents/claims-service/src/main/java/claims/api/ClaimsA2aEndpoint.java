package claims.api;

import akka.javasdk.a2a.A2aAgents;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.a2a.A2aEndpoint;
import claims.application.ClaimReviewWorkflow;
import claims.application.ClaimStatusAgent;

// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@A2aEndpoint(
  name = "Claims",
  description = "Answers questions about insurance claims and reviews them",
  version = "1.0.0"
)
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class ClaimsA2aEndpoint extends AbstractA2aEndpoint {

  @Override
  protected A2aAgents agents() {
    return A2aAgents.create()
      .agent(ClaimStatusAgent.class) // <1>
      .taskWorkflow(ClaimReviewWorkflow.class); // <2>
  }
}
