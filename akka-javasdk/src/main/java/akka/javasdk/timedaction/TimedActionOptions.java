/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.timedaction;

import akka.javasdk.impl.ComponentOptions;
import akka.javasdk.impl.timedaction.TimedActionOptionsImpl;

import java.util.Collections;

/** Options for TimedActions */
public interface TimedActionOptions extends ComponentOptions {

  /** Create default options for an action. */
  static TimedActionOptions defaults() {
    return new TimedActionOptionsImpl(Collections.emptySet());
  }
}
