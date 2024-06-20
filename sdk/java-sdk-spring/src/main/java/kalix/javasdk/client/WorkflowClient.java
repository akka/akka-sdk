/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import kalix.javasdk.workflow.Workflow;

/**
 * Not for user extension
 */
@DoNotInherit
public interface WorkflowClient {

  /**
   * Pass in a Workflow method reference annotated as a REST endpoint, e.g. <code>MyWorkflow::start</code>
   */
  <T, R> NativeComponentMethodRef<R> method(Function<T, Workflow.Effect<R>> methodRef);

  /**
   * Pass in a Workflow method reference annotated as a REST endpoint, e.g. <code>MyWorkflow::start</code>
   */
  <T, A1, R> NativeComponentMethodRef1<A1, R> method(Function2<T, A1, Workflow.Effect<R>> methodRef);

}
