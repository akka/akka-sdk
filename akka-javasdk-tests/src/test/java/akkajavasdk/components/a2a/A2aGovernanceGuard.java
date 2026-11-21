/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.a2a;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.ToolCallGuardrail;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Denies calls to the {@code pay} skill of remote A2A agents, from agent tools and from workflow
 * steps alike, and records every call it sees.
 */
public class A2aGovernanceGuard implements ToolCallGuardrail {

  public static final Queue<CallContext> calls = new ConcurrentLinkedQueue<>();

  @Override
  public Decision decide(CallContext ctx) {
    calls.add(ctx);
    if (ctx.origin() instanceof ToolOrigin.RemoteA2a remote && remote.skill().equals("pay")) {
      return new Decision.Deny("payments need a person");
    }
    return new Decision.Allow();
  }
}
