/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import akka.platform.javasdk.annotations.ComponentId;

@ComponentId("tracing-action")
public class TestTracingAction extends Action {

  Logger logger = LoggerFactory.getLogger(TestTracingAction.class);

  public Effect<String> endpoint() {
    logger.info("registering a logging event");
    return effects().reply(
        actionContext().metadata().traceContext().traceParent().orElse("not-found"));
  }
}
