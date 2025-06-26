/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.timedaction.TimedAction;

public interface TimedActionClient {
  /**
   * Pass in an Action method reference, e.g. {@code MyAction::create}
   */
  <T, R> ComponentDeferredMethodRef<R> method(Function<T, TimedAction.Effect> methodRef);

  /**
   * Pass in an Action method reference, e.g. {@code MyAction::create}
   */
  <T, A1, R> ComponentDeferredMethodRef1<A1, R> method(Function2<T, A1, TimedAction.Effect> methodRef);

}
