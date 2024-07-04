/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.view;

import akka.platform.javasdk.Context;

/** Context for views. */
public interface ViewContext extends Context {
  /**
   * The id of the view that this context is for.
   *
   * @return The view id.
   */
  String viewId();
}
