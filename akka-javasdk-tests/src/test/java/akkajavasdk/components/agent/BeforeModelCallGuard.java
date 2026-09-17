/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Decision;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.ModelGuardrail;
import java.util.List;

// Denies at before-model-call, echoing the system message and the latest user text into the reason.
// before-model-call is the only boundary that carries the conversation, so echoing it lets the test
// assert the boundary fired before the model and received the full conversation.
public class BeforeModelCallGuard implements ModelGuardrail {

  @Override
  public Decision decide(CallContext ctx) {
    if (ctx.conversation().isEmpty()) {
      return new Decision.Deny("no conversation at " + ctx.boundary());
    }

    var conversation = ctx.conversation().get();
    return new Decision.Deny(
        "before-model-call saw system=["
            + conversation.systemMessage()
            + "] user=["
            + lastUserText(conversation.messages())
            + "]");
  }

  private static String lastUserText(List<CallContext.ConversationMessage> messages) {
    var last = messages.get(messages.size() - 1);
    if (last instanceof CallContext.ConversationMessage.UserMessage userMessage
        && userMessage.contents().get(0) instanceof MessageContent.TextMessageContent text) {
      return text.text();
    }
    return "";
  }
}
