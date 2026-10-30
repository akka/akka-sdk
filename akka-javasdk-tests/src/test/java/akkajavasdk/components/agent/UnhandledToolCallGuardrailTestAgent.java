/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import java.util.concurrent.atomic.AtomicInteger;

/** An agent whose tool-call guardrail denies every tool call. The denial reaches the caller. */
@Component(id = "unhandled-tool-call-guardrail-test-agent")
public class UnhandledToolCallGuardrailTestAgent extends Agent {

  public static final AtomicInteger toolCalls = new AtomicInteger();

  public Effect<String> ask(String question) {
    return effects().systemMessage("You are a helpful assistant").userMessage(question).thenReply();
  }

  @FunctionTool(description = "Deletes the account of the customer")
  private String deleteAccount() {
    toolCalls.incrementAndGet();
    return "deleted";
  }
}
