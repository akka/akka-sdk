/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.GuardrailContext;
import akka.javasdk.agent.ToolCallGuardrail;

/**
 * Denies every call it sees to the {@code check-claim} skill. The configuration scopes it to remote
 * agents with {@code remote-agents}, so the calls it sees are the ones the scope selects.
 */
public class A2aScopedDenyGuard implements ToolCallGuardrail {

  private final String name;

  public A2aScopedDenyGuard(GuardrailContext context) {
    this.name = context.name();
  }

  @Override
  public Decision decide(CallContext ctx) {
    if (ctx.origin() instanceof ToolOrigin.RemoteA2a remote
        && remote.skill().equals("check-claim")) {
      return new Decision.Deny("scoped by [" + name + "]");
    }
    return new Decision.Allow();
  }
}
