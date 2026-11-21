package com.example.api;

import akka.javasdk.a2a.A2aAgents;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.a2a.A2aEndpoint;
import com.example.application.ClaimCheckAgent;
import com.example.application.ExampleA2aTaskWorkflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::endpoint-class[]
@A2aEndpoint( // <1>
  name = "Claims",
  description = "Checks insurance claims",
  version = "1.0.0"
)
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET)) // <2>
// end::endpoint-class[]
// tag::jwt[]
@JWT(
  validate = JWT.JwtMethodMode.BEARER_TOKEN,
  bearerTokenIssuers = "https://idp.example.com"
)
// end::jwt[]
// tag::endpoint-class[]
public class ExampleA2aEndpoint extends AbstractA2aEndpoint { // <3>

  private static final Logger logger = LoggerFactory.getLogger(ExampleA2aEndpoint.class);

  @Override
  protected A2aAgents agents() { // <4>
    // end::endpoint-class[]
    // tag::request-context[]
    requestContext()
      .contextId() // <1>
      .ifPresent(contextId ->
        logger.info(
          "Message in context {} from {}",
          contextId,
          requestContext().getJwtClaims().subject().orElse("unknown") // <2>
        ));
    // end::request-context[]
    // tag::endpoint-class[]
    // end::endpoint-class[]
    // tag::task-workflow[]
    return A2aAgents.create()
      .agent(ClaimCheckAgent.class)
      .taskWorkflow(ExampleA2aTaskWorkflow.class); // <1>
    // end::task-workflow[]
    // tag::endpoint-class[]
  }
}
// end::endpoint-class[]
