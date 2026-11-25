/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import java.util.Set;

/** Root class of all contexts. */
public interface Context {

  /** Returns the region where this instance is running. */
  String selfRegion();

  /**
   * Returns the regions that are marked as down, as this region knows them.
   *
   * <p>An operator marks a region as down with {@code akka project settings down-region} and brings
   * it up again with {@code akka project settings bring-up-region}. The command is sent to each
   * region, and a region lists only the regions for which it got the command. The set includes
   * {@link #selfRegion()} when this region is marked as down.
   *
   * <p>Each call returns the current value, which can change while the service runs. The set is
   * empty when no region is marked as down, and when the service does not run in several regions,
   * such as in local development and in tests.
   *
   * @return the names of the regions that are marked as down, in the form that {@link
   *     #selfRegion()} returns. The set cannot be modified.
   */
  Set<String> downedRegions();
}
