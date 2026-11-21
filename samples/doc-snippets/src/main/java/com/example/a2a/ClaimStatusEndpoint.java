package com.example.a2a;

import akka.javasdk.a2a.A2aClient;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.RemoteA2aAgentClient;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;

// tag::for-agent[]
@HttpEndpoint("/claim-status")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class ClaimStatusEndpoint {

  private final RemoteA2aAgentClient claimsAgent;

  public ClaimStatusEndpoint(A2aClient a2aClient) { // <1>
    this.claimsAgent = a2aClient.forAgent("claims"); // <2>
  }

  @Get("/{claimId}")
  public String status(String claimId) {
    var message = A2aMessage.user("What is the status of claim " + claimId + "?"); // <3>
    var reply = claimsAgent.send("claim-status", message).invoke(); // <4>
    return switch (reply) { // <5>
      case A2aSendMessageResponse.Task task -> task.task().status().state().name();
      case A2aSendMessageResponse.Message answer -> "answered without a task";
    };
  }
}
// end::for-agent[]
