/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

/** Root class of all contexts. */
public interface Context {

  /** Returns the region where this instance is running. */
  String selfRegion();
}
