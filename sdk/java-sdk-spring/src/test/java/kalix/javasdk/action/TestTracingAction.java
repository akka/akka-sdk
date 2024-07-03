/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kalix.javasdk.annotations.ActionId;

@ActionId("tracing-action")
public class TestTracingAction extends Action {

  Logger logger = LoggerFactory.getLogger(TestTracingAction.class);

  public Effect<String> endpoint() {
    logger.info("registering a logging event");
    return effects().reply(
        actionContext().metadata().traceContext().traceParent().orElse("not-found"));
  }
}
