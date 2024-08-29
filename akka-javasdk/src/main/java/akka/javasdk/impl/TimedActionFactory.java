/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.annotation.InternalApi;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.timedaction.TimedActionContext;
import akka.javasdk.impl.timedaction.TimedActionRouter;

/**
 * Low level interface to implement {@link TimedAction} components.
 *
 * <p>Generally, this should not be needed, instead, a class extending a generated abstract {@link
 * TimedAction} should be used.
 *
 * <p>INTERNAL API
 */
@InternalApi
public interface TimedActionFactory {
  TimedActionRouter<?> create(TimedActionContext context);
}
