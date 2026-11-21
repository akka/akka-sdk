/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.a2a.A2aAgents;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.a2a.A2aEndpoint;
import akka.javasdk.annotations.a2a.A2aSkill;

@A2aEndpoint(name = "Claims", version = "1.0.0", publicUrl = "https://claims.example.com")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "https://idp.example.com")
public class ValidA2aEndpoint extends AbstractA2aEndpoint {

  @Override
  protected A2aAgents agents() {
    return A2aAgents.create().agent(ClaimsAgent.class);
  }

  @Component(id = "claims-agent")
  @A2aSkill(id = "check-claim", name = "Check a claim")
  @A2aSkill(id = "explain.claim_v2", name = "Explain a claim")
  public static class ClaimsAgent extends Agent {
    public Effect<String> ask(String question) {
      return effects().userMessage(question).thenReply();
    }
  }
}
