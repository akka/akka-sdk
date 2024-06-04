/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

import akka.japi.function.Function;
import akka.japi.function.Function2;
import kalix.javasdk.workflow.Workflow;
import kalix.spring.impl.KalixClient;

import java.util.List;
import java.util.Optional;

public class WorkflowClient {

  private final KalixClient kalixClient;
  private final String workflowId;

  public WorkflowClient(KalixClient kalixClient, String workflowId) {
    this.kalixClient = kalixClient;
    this.workflowId = workflowId;
  }


  /**
   * Pass in a Workflow method reference annotated as a REST endpoint, e.g. <code>MyWorkflow::start</code>
   */
  public <T, R> ComponentMethodRef<R> method(Function<T, Workflow.Effect<R>> methodRef) {
    return new ComponentMethodRef<>(kalixClient, methodRef, workflowId, Optional.empty());
  }

  /**
   * Pass in a Workflow method reference annotated as a REST endpoint, e.g. <code>MyWorkflow::start</code>
   */
  public <T, A1, R> ComponentMethodRef1<A1, R> method(Function2<T, A1, Workflow.Effect<R>> methodRef) {
    return new ComponentMethodRef1<>(kalixClient, methodRef, List.of(workflowId), Optional.empty());
  }


}
