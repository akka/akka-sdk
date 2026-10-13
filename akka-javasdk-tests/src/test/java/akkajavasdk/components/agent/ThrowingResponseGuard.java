/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.AgentResponseGuardrail;
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;

public class ThrowingResponseGuard implements AgentResponseGuardrail {
  private final String errorMessage;

  public ThrowingResponseGuard(GuardrailContext context) {
    this.errorMessage = context.config().getString("error-message");
  }

  @Override
  public Decision decide(CallContext ctx) {
    throw new IllegalStateException(errorMessage);
  }
}
