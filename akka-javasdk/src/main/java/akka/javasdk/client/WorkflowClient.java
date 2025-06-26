/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.workflow.Workflow;

/**
 * Not for user extension
 */
@DoNotInherit
public interface WorkflowClient {

  /**
   * Pass in a Workflow method reference annotated as a REST endpoint, e.g. {@code MyWorkflow::start}
   */
  <T, R> ComponentMethodRef<R> method(Function<T, Workflow.Effect<R>> methodRef);

  /**
   * Pass in a Workflow method reference annotated as a REST endpoint, e.g. {@code MyWorkflow::start}
   */
  <T, A1, R> ComponentMethodRef1<A1, R> method(Function2<T, A1, Workflow.Effect<R>> methodRef);

}
