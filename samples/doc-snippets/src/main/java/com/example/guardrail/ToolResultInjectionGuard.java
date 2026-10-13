package com.example.guardrail;

// tag::all[]
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.Guardrail.Message;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.ModelCallGuardrail;

public class ToolResultInjectionGuard implements ModelCallGuardrail { // <1>

  @Override
  public Decision decide(CallContext ctx) {
    for (var message : ctx.newMessages()) { // <2>
      if (message instanceof Message.ToolCallResponse result) { // <3>
        for (var content : result.contents()) {
          if (
            content instanceof MessageContent.TextMessageContent text &&
            text.text().contains("ignore previous instructions")
          ) {
            return new Decision.Deny(
              "Tool '%s' returned an injection attempt.".formatted(result.name())
            ); // <4>
          }
        }
      }
    }
    return new Decision.Allow();
  }
}
// end::all[]
