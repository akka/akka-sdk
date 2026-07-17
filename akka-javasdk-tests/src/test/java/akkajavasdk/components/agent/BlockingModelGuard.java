/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ModelGuardrail;

public class BlockingModelGuard implements ModelGuardrail {
  private final String blockReason;

  public BlockingModelGuard(GuardrailContext context) {
    this.blockReason = context.config().getString("block-reason");
  }

  @Override
  public Decision decide(CallContext ctx) {
    return new Decision.Deny(blockReason);
  }
}
