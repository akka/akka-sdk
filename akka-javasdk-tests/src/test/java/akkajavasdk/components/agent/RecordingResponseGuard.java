/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.AgentResponseGuardrail;
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.Guardrail;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Allows every reply and records the last reply it saw, per session.
public class RecordingResponseGuard implements AgentResponseGuardrail {

  public static final Map<String, Guardrail.Message.AiMessage> replyBySession =
      new ConcurrentHashMap<>();

  @Override
  public Decision decide(CallContext ctx) {
    replyBySession.put(ctx.sessionId(), ctx.reply());
    return new Decision.Allow();
  }
}
