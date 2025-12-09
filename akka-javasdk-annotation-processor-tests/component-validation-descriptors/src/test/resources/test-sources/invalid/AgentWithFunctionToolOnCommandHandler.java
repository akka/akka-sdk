/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;

@Component(id = "agent-with-function-tool-on-command-handler")
public class AgentWithFunctionToolOnCommandHandler extends Agent {

  @FunctionTool(description = "This should not be allowed on command handler")
  public Effect<String> query(String request) {
    return effects().reply("response");
  }
}
