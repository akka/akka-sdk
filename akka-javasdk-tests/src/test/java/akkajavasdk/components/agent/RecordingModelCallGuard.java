/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.ModelCallGuardrail;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Allows every model call and records the new message types it saw, per session.
public class RecordingModelCallGuard implements ModelCallGuardrail {

  public static final Map<String, List<List<String>>> newMessageTypesBySession =
      new ConcurrentHashMap<>();

  @Override
  public Decision decide(CallContext ctx) {
    var types = ctx.newMessages().stream().map(m -> m.getClass().getSimpleName()).toList();
    newMessageTypesBySession
        .computeIfAbsent(ctx.sessionId(), id -> new CopyOnWriteArrayList<>())
        .add(types);
    return new Decision.Allow();
  }
}
