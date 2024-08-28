/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

/**
 * Represents the result of an Action handling a command when run in through the testkit.
 *
 * <p>Not for user extension, returned by the testkit.
 */
public interface TimedActionResult {

  /** @return true if the call had an effect with a reply, false if not */
  boolean isDone();

  /** @return true if the call was async, false if not */
  boolean isAsync();

  /** @return true if the call was an error, false if not */
  boolean isError();

  /**
   * @return The error description returned or throws if the effect returned by the action was not
   *     an error
   */
  String getError();
}
