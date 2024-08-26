/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl;

import akka.platform.javasdk.timedaction.TimedAction;
import akka.platform.javasdk.timedaction.TimedActionContext;
import akka.platform.javasdk.impl.timedaction.TimedActionRouter;

/**
 * Low level interface to implement {@link TimedAction} components.
 *
 * <p>Generally, this should not be needed, instead, a class extending a generated abstract {@link
 * TimedAction} should be used.
 */
public interface TimedActionFactory {
  TimedActionRouter<?> create(TimedActionContext context);
}
