/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.action.ActionCreationContext;
import akka.platform.javasdk.impl.action.ActionRouter;

/**
 * Low level interface to implement {@link Action} components.
 *
 * <p>Generally, this should not be needed, instead, a class extending a generated abstract {@link
 * Action} should be used.
 */
public interface ActionFactory {
  ActionRouter<?> create(ActionCreationContext context);
}
