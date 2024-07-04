/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.workflow;

import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.JWT;
import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.workflow.Workflow;

public class WorkflowTestModels {

  @TypeId("transfer-workflow")
  public static class TransferWorkflow extends Workflow<WorkflowState> {
    @Override
    public WorkflowDef<WorkflowState> definition() {
      return null;
    }

    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }

    public Effect<WorkflowState> getState() {
      return null;
    }
  }

  @TypeId("transfer-workflow")
  public static class WorkflowWithMethodLevelJWT extends Workflow<WorkflowState> {
    @Override
    public WorkflowDef<WorkflowState> definition() {
      return null;
    }

    @JWT(
        validate = JWT.JwtMethodMode.BEARER_TOKEN,
        bearerTokenIssuer = {"a", "b"},
        staticClaims = {
            @JWT.StaticClaim(claim = "role", value = "method-admin"),
            @JWT.StaticClaim(claim = "aud", value = "${ENV}.kalix.io")
        })
    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }
  }

  @TypeId("transfer-workflow")
  @JWT(
    validate = JWT.JwtMethodMode.BEARER_TOKEN,
    bearerTokenIssuer = {"c", "d"},
    staticClaims = {
        @JWT.StaticClaim(claim = "role", value = "admin"),
        @JWT.StaticClaim(claim = "aud", value = "${ENV}")
    })
  public static class WorkflowWithServiceLevelJWT extends Workflow<WorkflowState> {
    @Override
    public WorkflowDef<WorkflowState> definition() {
      return null;
    }

    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }
  }

  @TypeId("transfer-workflow")
  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class WorkflowWithAcl extends Workflow<WorkflowState> {

    @Override
    public WorkflowDef<WorkflowState> definition() {
      return null;
    }
  }

  @TypeId("transfer-workflow")
  public static class WorkflowWithMethodLevelAcl extends Workflow<WorkflowState> {

    @Override
    public WorkflowDef<WorkflowState> definition() {
      return null;
    }

    @Acl(allow = @Acl.Matcher(service = "test"))
    public Effect<String> startTransfer(StartWorkflow startWorkflow) {
      return null;
    }
  }
}
