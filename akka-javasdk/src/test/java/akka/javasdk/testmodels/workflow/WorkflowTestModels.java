/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.workflow;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.JWT;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;

public class WorkflowTestModels {

  @ComponentId("transfer-workflow")
  public static class TransferWorkflow extends Workflow<WorkflowState> {

    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }
    public Effect<WorkflowState> getState() {
      return null;
    }
  }

  @ComponentId("transfer-workflow")
  public static class WorkflowWithMethodLevelJWT extends Workflow<WorkflowState> {

    @JWT(
        validate = JWT.JwtMethodMode.BEARER_TOKEN,
        bearerTokenIssuers = {"a", "b"},
        staticClaims = {
            @JWT.StaticClaim(claim = "role", values = "method-admin"),
            @JWT.StaticClaim(claim = "aud", values = "${ENV}.kalix.io")
        })
    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }
  }

  @ComponentId("transfer-workflow")
  @JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuers = {"c", "d"},
    staticClaims = {
        @JWT.StaticClaim(claim = "role", values = "admin"),
        @JWT.StaticClaim(claim = "aud", values = "${ENV}")
    })
  public static class WorkflowWithServiceLevelJWT extends Workflow<WorkflowState> {

    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }
  }

  @ComponentId("transfer-workflow")
  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class WorkflowWithAcl extends Workflow<WorkflowState> {

  }

  @ComponentId("transfer-workflow")
  public static class WorkflowWithMethodLevelAcl extends Workflow<WorkflowState> {

    @Acl(allow = @Acl.Matcher(service = "test"))
    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }
  }
}
