package com.example.guardrail;

// tag::all[]
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.ToolGuardrail;

public class PaymentToolGuard implements ToolGuardrail { // <1>

  @Override
  public Decision decide(CallContext ctx) { // <2>
    if (!ctx.toolName().equals("transferFunds")) {
      return new Decision.Allow();
    }
    // ctx exposes agentId(), toolName(), toolCallId(), and the raw JSON arguments()
    if (ctx.arguments().contains("\"amount\":10000")) { // <3>
      return new Decision.Deny("transfers of 10000 or more require human approval"); // <4>
    }
    return new Decision.Allow();
  }
}
// end::all[]
