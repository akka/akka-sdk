/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.ToolCallGuardrail;
import java.util.concurrent.atomic.AtomicReference;

public class RecordingAllowingToolGuard implements ToolCallGuardrail {

  public static final AtomicReference<RecordingDenyingToolGuard.Recorded> lastCall =
      new AtomicReference<>();

  @Override
  public Decision decide(CallContext ctx) {
    lastCall.set(
        new RecordingDenyingToolGuard.Recorded(ctx.toolName(), ctx.arguments(), ctx.origin()));
    return new Decision.Allow();
  }
}
