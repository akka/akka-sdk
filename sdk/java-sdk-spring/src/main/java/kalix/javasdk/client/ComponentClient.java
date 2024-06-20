/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

import akka.annotation.DoNotInherit;

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
 *     componentClient.forValueEntity(userId)
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
  /** Select Action as a call target component. */
  ActionClient forAction();

  /**
   * Select ValueEntity as a call target component.
   *
   * @param valueEntityId - value entity id used to create a call.
   */
  ValueEntityClient forValueEntity(String valueEntityId);

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
