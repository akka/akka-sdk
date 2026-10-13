/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ToolCallGuardrail;

public class DenyingToolGuard implements ToolCallGuardrail {
  private final String denyReason;

  public DenyingToolGuard(GuardrailContext context) {
    this.denyReason = context.config().getString("deny-reason");
  }

  @Override
  public Decision decide(CallContext ctx) {
    return new Decision.Deny(denyReason + " [" + ctx.toolName() + "]");
  }
}
