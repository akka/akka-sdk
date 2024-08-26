/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.timedaction;

import akka.platform.javasdk.impl.ComponentOptions;
import akka.platform.javasdk.impl.timedaction.TimedActionOptionsImpl;

import java.util.Collections;

/** Options for TimedActions */
public interface TimedActionOptions extends ComponentOptions {

  /** Create default options for an action. */
  static TimedActionOptions defaults() {
    return new TimedActionOptionsImpl(Collections.emptySet());
  }
}
