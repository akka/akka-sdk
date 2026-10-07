/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.Guardrail.Message;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.ModelCallGuardrail;
import java.util.List;

// Denies at before-model-call, echoing the system message and the new user text into the reason.
public class BeforeModelCallGuard implements ModelCallGuardrail {

  @Override
  public Decision decide(CallContext ctx) {
    return new Decision.Deny(
        "before-model-call saw system=["
            + ctx.systemMessage()
            + "] user=["
            + lastUserText(ctx.newMessages())
            + "]");
  }

  private static String lastUserText(List<Message> messages) {
    var last = messages.getLast();
    if (last instanceof Message.UserMessage userMessage
        && userMessage.contents().get(0) instanceof MessageContent.TextMessageContent text) {
      return text.text();
    }
    return "";
  }
}
