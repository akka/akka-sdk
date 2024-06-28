/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.action;

import kalix.javasdk.annotations.ActionId;

@ActionId("tracing-action")
public class TestTracingAction extends Action {

  public Effect<String> endpoint() {
    return effects().reply(
        actionContext().metadata().traceContext().traceParent().orElse("not-found"));
  }
}
