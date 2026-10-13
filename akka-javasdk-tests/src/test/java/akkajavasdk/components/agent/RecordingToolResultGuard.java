/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.Guardrail;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.ModelCallGuardrail;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

// Allows every model call. Records the text of each new tool result, per session.
public class RecordingToolResultGuard implements ModelCallGuardrail {

  public static final Map<String, List<String>> toolResultsBySession = new ConcurrentHashMap<>();

  @Override
  public Decision decide(CallContext ctx) {
    for (var message : ctx.newMessages()) {
      if (message instanceof Guardrail.Message.ToolCallResponse response) {
        var text =
            response.contents().stream()
                .filter(c -> c instanceof MessageContent.TextMessageContent)
                .map(c -> ((MessageContent.TextMessageContent) c).text())
                .collect(Collectors.joining());

        toolResultsBySession
            .computeIfAbsent(ctx.sessionId(), id -> new CopyOnWriteArrayList<>())
            .add(text);
      }
    }
    return new Decision.Allow();
  }
}
