/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.agent.ModelCallGuardrail;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Allows every model call. Records the new message types and the AI messages it saw, per session.
public class RecordingModelCallGuard implements ModelCallGuardrail {

  public static final Map<String, List<List<String>>> newMessageTypesBySession =
      new ConcurrentHashMap<>();

  public static final Map<String, List<Guardrail.Message.AiMessage>> aiMessagesBySession =
      new ConcurrentHashMap<>();

  @Override
  public Decision decide(CallContext ctx) {
    var types = ctx.newMessages().stream().map(m -> m.getClass().getSimpleName()).toList();
    newMessageTypesBySession
        .computeIfAbsent(ctx.sessionId(), id -> new CopyOnWriteArrayList<>())
        .add(types);

    for (var message : ctx.messages()) {
      if (message instanceof Guardrail.Message.AiMessage aiMessage) {
        aiMessagesBySession
            .computeIfAbsent(ctx.sessionId(), id -> new CopyOnWriteArrayList<>())
            .add(aiMessage);
      }
    }

    return new Decision.Allow();
  }
}
