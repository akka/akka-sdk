/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.a2a.A2aAgents;
import akka.javasdk.a2a.AbstractA2aEndpoint;
import akka.javasdk.annotations.a2a.A2aEndpoint;

public class TwoA2aEndpoints {

  @A2aEndpoint(name = "First", version = "1")
  public static class First extends AbstractA2aEndpoint {
    @Override
    protected A2aAgents agents() {
      return A2aAgents.create();
    }
  }

  @A2aEndpoint(path = "/other", name = "Second", version = "1")
  public static class Second extends AbstractA2aEndpoint {
    @Override
    protected A2aAgents agents() {
      return A2aAgents.create();
    }
  }
}
