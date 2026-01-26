/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "agent-with-function-tool-on-private-method")
public class AgentWithFunctionToolOnPrivateMethod extends Agent {

  public Effect<String> handle(String input) {
    // Use the private method annotated with @FunctionTool
    privateHelperMethod();
    return effects().reply("handled");
  }

  // @FunctionTool is allowed on private methods for Agents (as long as it's not a command handler)
  @FunctionTool(description = "This is allowed on private methods for Agents")
  private String privateHelperMethod() {
    return "helper";
  }
}
