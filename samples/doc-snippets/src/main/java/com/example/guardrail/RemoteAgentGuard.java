package com.example.guardrail;

// tag::all[]
import akka.javasdk.agent.Decision;
import akka.javasdk.agent.ToolCallGuardrail;

public class RemoteAgentGuard implements ToolCallGuardrail {

  @Override
  public Decision decide(CallContext ctx) {
    if (
      ctx.origin() instanceof ToolOrigin.RemoteA2a remote && // <1>
      remote.agentName().equals("billing") &&
      remote.skill().equals("refund") // <2>
    ) {
      return new Decision.Deny("refunds through the billing agent require human approval");
    }
    return new Decision.Allow();
  }
}
// end::all[]
