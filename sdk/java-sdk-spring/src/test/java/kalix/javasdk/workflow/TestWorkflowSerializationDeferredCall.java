/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.workflow;

import kalix.javasdk.Metadata;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.impl.MetadataImpl;
import kalix.javasdk.impl.RestDeferredCall;
import scala.Function1;

import java.util.concurrent.CompletionStage;

@TypeId("workflow")
public class TestWorkflowSerializationDeferredCall extends Workflow<String> {

  @Override
  public WorkflowDef<String> definition() {
    Function1<Metadata, CompletionStage<Result>> empty = null;
    var testStep = step("test")
        .call(() -> new RestDeferredCall<>("payload", MetadataImpl.Empty(), "some-service", "some-method", empty))
        .andThen(Result.class, result -> effects().updateState("success").end());

    return workflow().addStep(testStep);
  }

  public Effect<String> start() {
    return effects()
        .updateState("empty")
        .transitionTo("test")
        .thenReply("ok");
  }

  public Effect<String> get() {
    return effects().reply(currentState());
  }
}
