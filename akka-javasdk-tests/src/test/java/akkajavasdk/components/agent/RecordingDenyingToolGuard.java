/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ToolCallGuardrail;
import java.util.concurrent.atomic.AtomicReference;

public class RecordingDenyingToolGuard implements ToolCallGuardrail {

  public record Recorded(String toolName, String arguments, ToolOrigin origin) {}

  public static final AtomicReference<Recorded> lastCall = new AtomicReference<>();

  private final String denyReason;

  public RecordingDenyingToolGuard(GuardrailContext context) {
    this.denyReason = context.config().getString("deny-reason");
  }

  @Override
  public Decision decide(CallContext ctx) {
    lastCall.set(new Recorded(ctx.toolName(), ctx.arguments(), ctx.origin()));
    return new Decision.Deny(denyReason + " [" + ctx.toolName() + "]");
  }
}
