/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aReply;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.a2a.A2aEndpoint;
import akka.javasdk.annotations.a2a.A2aSkill;

@A2aEndpoint(
    path = "/a2a/test",
    name = "Test agent",
    description = "An agent for the tests",
    version = "1.2.3",
    providerOrganization = "Example Inc",
    providerUrl = "https://example.com",
    skills = {
      @A2aSkill(
          id = "echo",
          name = "Echo",
          description = "Echoes the text of the message",
          tags = {"test"},
          examples = {"Say hello"})
    })
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class TestA2aEndpoint extends AbstractA2aEndpoint {

  @Override
  public A2aReply onMessage(A2aMessage message) {
    if (message.text().equals("fail")) {
      throw new IllegalStateException("the endpoint failed");
    }
    return A2aReply.text(
        "echo: "
            + message.text()
            + "|context="
            + requestContext().contextId()
            + "|session="
            + requestContext().sessionId()
            + "|caller="
            + requestContext().callerId().orElse("none"));
  }
}
