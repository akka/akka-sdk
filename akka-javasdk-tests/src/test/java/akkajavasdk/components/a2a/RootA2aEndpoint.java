/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aReply;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.a2a.A2aEndpoint;

@A2aEndpoint(name = "Root agent", description = "The agent at the root path", version = "1.0.0")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class RootA2aEndpoint extends AbstractA2aEndpoint {

  @Override
  public A2aReply onMessage(A2aMessage message) {
    return A2aReply.text("root: " + message.text());
  }
}
