/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit;

import io.grpc.Status;

import java.util.concurrent.CompletionStage;
import java.util.List;

/**
 * Represents the result of an Action handling a command when run in through the testkit.
 *
 * <p>Not for user extension, returned by the testkit.
 *
 * @param <T> The type of reply that is expected from invoking a command handler
 */
public interface ActionResult<T> {

  /** @return true if the call had an effect with a reply, false if not */
  boolean isReply();

  /**
   * @return The reply message if the returned effect was a reply or throws if the returned effect
   *     was not a reply.
   */
  T getReply();


  /** @return true if the call was async, false if not */
  boolean isAsync();

  /**
   * @return The future result if the returned effect was an async effect or throws if the returned
   *     effect was not async.
   */
  CompletionStage<ActionResult<T>> getAsyncResult();

  /** @return true if the returned effect was ignore, false if not */
  boolean isIgnore();

  /** @return true if the call was an error, false if not */
  boolean isError();

  /**
   * @return The error description returned or throws if the effect returned by the action was not
   *     an error
   */
  String getError();

  /**
   * @return The error status code or throws if the effect returned by the action was not an error.
   */
  Status.Code getErrorStatusCode();

}