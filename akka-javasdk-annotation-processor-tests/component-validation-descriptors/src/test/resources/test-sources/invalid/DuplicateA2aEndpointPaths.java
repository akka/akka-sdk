/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aReply;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.a2a.A2aEndpoint;

public class DuplicateA2aEndpointPaths {

  @A2aEndpoint(path = "/a2a/claims", name = "First", description = "First", version = "1.0.0")
  public static class First extends AbstractA2aEndpoint {
    @Override
    public A2aReply onMessage(A2aMessage message) {
      return A2aReply.text("first");
    }
  }

  @A2aEndpoint(path = "/a2a/claims/", name = "Second", description = "Second", version = "1.0.0")
  public static class Second extends AbstractA2aEndpoint {
    @Override
    public A2aReply onMessage(A2aMessage message) {
      return A2aReply.text("second");
    }
  }

  @A2aEndpoint(name = "Root", description = "Root", version = "1.0.0")
  public static class DefaultRoot extends AbstractA2aEndpoint {
    @Override
    public A2aReply onMessage(A2aMessage message) {
      return A2aReply.text("root");
    }
  }

  @A2aEndpoint(path = "", name = "Other root", description = "Other root", version = "1.0.0")
  public static class EmptyRoot extends AbstractA2aEndpoint {
    @Override
    public A2aReply onMessage(A2aMessage message) {
      return A2aReply.text("root");
    }
  }
}
