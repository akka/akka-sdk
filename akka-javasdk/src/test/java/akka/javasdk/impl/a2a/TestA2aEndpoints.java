/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aReply;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.a2a.A2aEndpoint;
import akka.javasdk.annotations.a2a.A2aSkill;

public class TestA2aEndpoints {

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
  @JWT(validate = JWT.JwtMethodMode.BEARER_TOKEN, bearerTokenIssuers = "https://issuer.example.com")
  @A2aEndpoint(
      path = "/a2a/claims",
      name = "Claims",
      description = "Answers questions about claims",
      version = "1.2.3",
      providerOrganization = "Example Inc",
      providerUrl = "https://example.com",
      skills = {
        @A2aSkill(
            id = "status",
            name = "Claim status",
            description = "Tells the status of a claim",
            tags = {"claims"},
            examples = {"What is the status of claim 17?"})
      })
  public static class ClaimsEndpoint extends AbstractA2aEndpoint {
    @Override
    public A2aReply onMessage(A2aMessage message) {
      return A2aReply.text(
          "session="
              + requestContext().sessionId()
              + ",context="
              + requestContext().contextId()
              + ",text="
              + message.text());
    }
  }

  @A2aEndpoint(name = "Root", description = "Root agent", version = "1.0.0")
  public static class RootEndpoint extends AbstractA2aEndpoint {
    @Override
    public A2aReply onMessage(A2aMessage message) {
      return null;
    }
  }

  @A2aEndpoint(
      name = "Duplicate skills",
      description = "Has two skills with the same id",
      version = "1.0.0",
      skills = {
        @A2aSkill(id = "same", name = "One", description = "One"),
        @A2aSkill(id = "same", name = "Two", description = "Two")
      })
  public static class DuplicateSkillsEndpoint extends AbstractA2aEndpoint {
    @Override
    public A2aReply onMessage(A2aMessage message) {
      return A2aReply.text("ok");
    }
  }
}
