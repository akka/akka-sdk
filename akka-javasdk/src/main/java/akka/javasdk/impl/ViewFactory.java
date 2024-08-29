/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.annotation.InternalApi;
import akka.javasdk.impl.view.ViewUpdateRouter;
import akka.javasdk.view.View;
import akka.javasdk.view.ViewContext;

/**
 * Low level interface for handling messages in views.
 *
 * <p>Generally, this should not be needed, instead, a class extending a generated abstract {@link
 * View} should be used.
 *
 * <p>INTERNAL API
 */
@InternalApi
public interface ViewFactory {
  /**
   * Create a view handler for the given context.
   *
   * @param context The context.
   * @return The handler for the given context.
   */
  ViewUpdateRouter create(ViewContext context);
}
