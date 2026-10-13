/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ToolCallGuardrail;
import java.util.concurrent.atomic.AtomicReference;

public class RecordingDenyingToolGuard implements ToolCallGuardrail {

  public static final AtomicReference<CallContext> lastCall = new AtomicReference<>();

  private final String denyReason;

  public RecordingDenyingToolGuard(GuardrailContext context) {
    this.denyReason = context.config().getString("deny-reason");
  }

  @Override
  public Decision decide(CallContext ctx) {
    lastCall.set(ctx);
    return new Decision.Deny(denyReason + " [" + ctx.toolName() + "]");
  }
}
