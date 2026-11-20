/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.ToolCallGuardrail;
import java.util.concurrent.atomic.AtomicReference;

/** Denies calls to the {@code pay} skill of a remote A2A agent, and records the last call. */
public class RemoteA2aDenyingToolGuard implements ToolCallGuardrail {

  public static final AtomicReference<CallContext> lastCall = new AtomicReference<>();

  @Override
  public Decision decide(CallContext ctx) {
    lastCall.set(ctx);
    if (ctx.origin() instanceof ToolOrigin.RemoteA2a remote && remote.skill().equals("pay")) {
      return new Decision.Deny("payments need a person [" + ctx.toolName() + "]");
    }
    return new Decision.Allow();
  }
}
