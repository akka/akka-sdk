/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "valid-agent-with-function-tool")
public class ValidAgentWithFunctionTool extends Agent {

  // Command handler - no @FunctionTool
  public Effect<String> query(String request) {
    return effects().reply("response");
  }

  // Tool method - @FunctionTool is allowed
  @FunctionTool(description = "A helper tool for the agent")
  private String helperTool(String input) {
    return "processed: " + input;
  }
}
