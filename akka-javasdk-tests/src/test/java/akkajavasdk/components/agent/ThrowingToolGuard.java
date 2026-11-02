/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ToolCallGuardrail;
import java.util.concurrent.atomic.AtomicReference;

public class ThrowingToolGuard implements ToolCallGuardrail {

  public static final AtomicReference<CallContext> lastCall = new AtomicReference<>();

  private final String errorMessage;

  public ThrowingToolGuard(GuardrailContext context) {
    this.errorMessage = context.config().getString("error-message");
  }

  @Override
  public Decision decide(CallContext ctx) {
    lastCall.set(ctx);
    throw new IllegalStateException(errorMessage + " [" + ctx.toolName() + "]");
  }
}
