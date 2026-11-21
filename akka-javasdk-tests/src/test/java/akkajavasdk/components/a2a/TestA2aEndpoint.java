/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.javasdk.a2a.A2aAgents;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.a2a.A2aEndpoint;

@A2aEndpoint(
    name = "SDK test agents",
    description = "Agents of the SDK integration tests",
    version = "1.0.0")
@JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuers = {"https://issuer.a2a.example", "a2a-test-issuer"})
@Acl(
    allow = @Acl.Matcher(principal = Acl.Principal.ALL),
    deny = @Acl.Matcher(service = "blocked-service"))
public class TestA2aEndpoint extends AbstractA2aEndpoint {

  // The subject of the last message, read from the request context
  public static volatile String lastSubject;

  // Set by A2aInteropServiceTest. The compatibility kit selects no skill, so the first skill, the
  // kit task workflow, answers it and picks its behaviour by the prefix of the message id
  public static volatile boolean interop = false;

  @Override
  protected A2aAgents agents() {
    // The Agent Card route also calls agents(), without a token or a context id
    requestContext()
        .contextId()
        .ifPresent(c -> lastSubject = requestContext().getJwtClaims().subject().orElse(null));
    if (interop) {
      return A2aAgents.create().taskWorkflow(A2aKitTaskWorkflow.class).agent(A2aEchoAgent.class);
    }
    return A2aAgents.create()
        .agent(A2aEchoAgent.class)
        .agent(A2aAssistantAgent.class)
        .agent(A2aStreamingAgent.class)
        .taskWorkflow(A2aKitTaskWorkflow.class);
  }
}
