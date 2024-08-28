/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.timedaction.TimedAction;

/**
 * Utility to send requests to other Kalix components by composing a DeferredCall. To compose a
 * call:
 *
 * <ol>
 *   <li>select component type (and pass id if necessary)
 *   <li>select component method, by using Java method reference operator (::)
 *   <li>provide a request parameter (if required)
 * </ol>
 *
 * <p>Example of use on a cross-component call:
 *
 * <pre>{@code
 * public Effect<String> createUser(String userId, String email, String name) {
 *   //validation here
 *   var defCall =
 *     componentClient.forKeyValueEntity(userId)
 *       .method(UserEntity::createUser)
 *       .deferred(new CreateRequest(email, name));
 *   return effects().forward(defCall);
 * }
 * }</pre>
 *
 * Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface ComponentClient {
  /** Select {@link TimedAction} as a call target component. */
  TimedActionClient forTimedAction();

  /**
   * Select KeyValueEntity as a call target component.
   *
   * @param keyValueEntityId - key value entity id used to create a call.
   */
  KeyValueEntityClient forKeyValueEntity(String keyValueEntityId);

  /**
   * Select EventSourcedEntity as a call target component.
   *
   * @param eventSourcedEntityId - event sourced entity id used to create a call.
   */
  EventSourcedEntityClient forEventSourcedEntity(String eventSourcedEntityId);

  /**
   * Select Workflow as a call target component.
   *
   * @param workflowId - workflow id used to create a call.
   */
  WorkflowClient forWorkflow(String workflowId);

  /** Select View as a call target component. */
  ViewClient forView();
}
