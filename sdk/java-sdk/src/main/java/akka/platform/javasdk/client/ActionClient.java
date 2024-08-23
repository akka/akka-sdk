/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.client;

import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.platform.javasdk.action.Action;

public interface ActionClient {
  /**
   * Pass in an Action method reference, e.g. <code>MyAction::create</code>
   */
  <T, R> ComponentDeferredMethodRef<R> method(Function<T, Action.Effect> methodRef);

  /**
   * Pass in an Action method reference, e.g. <code>MyAction::create</code>
   */
  <T, A1, R> ComponentDeferredMethodRef1<A1, R> method(Function2<T, A1, Action.Effect> methodRef);

}
