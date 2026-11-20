package com.example.a2a;

import akka.javasdk.a2a.A2aClient;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.RemoteA2aAgentClient;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import java.util.List;

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
    var message = new A2aMessage(
      null,
      null,
      null,
      A2aRole.USER,
      List.of(A2aPart.text("What is the status of claim " + claimId + "?")),
      null,
      null,
      null
    );
    var reply = claimsAgent.send("claim-status", message).invoke(); // <3>
    return reply.task().status().state().name();
  }
}
// end::for-agent[]
