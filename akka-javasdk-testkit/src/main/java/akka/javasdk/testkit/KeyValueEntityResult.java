/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

/**
 * Represents the result of a KeyValueEntity handling a command when run in through the testkit.
 *
 * <p>Not for user extension, returned by the testkit.
 *
 * @param <R> The type of reply that is expected from invoking a command handler
 */
public interface KeyValueEntityResult<R> {

  /**
   * @return true if the call had an effect with a reply, false if not
   */
  boolean isReply();

  /**
   * @return The reply object from the handler if there was one. If the call had an effect without
   *     any reply an exception is thrown.
   */
  R getReply();

  /**
   * @return true if the call was an error, false if not
   */
  boolean isError();

  /** The error description. If the result was not an error an exception is thrown */
  String getError();

  /**
   * @return true if the call updated the entity state
   */
  boolean stateWasUpdated();

  /**
   * @return The updated state. If the state was not updated an exception is thrown
   */
  Object getUpdatedState();

  /**
   * @return true if the call deleted the entity
   */
  boolean stateWasDeleted();
}
