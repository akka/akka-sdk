/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

/**
 * Utility to send requests to other Kalix components by composing a DeferredCall. To compose a
 * call:
 *
 * <ol>
 *   <li>select component type (and pass id if necessary)
 *   <li>select component method, by using Java method reference operator (::)
 *   <li>provide parameters (if required)
 * </ol>
 *
 * <p>Example of use on a cross-component call:
 *
 * <pre>{@code
 * public Effect<String> createUser(String userId, String email, String name) {
 *   //validation here
 *   var defCall = componentClient.forValueEntity(userId).call(UserEntity::createUser).params(email, name);
 *   return effects().forward(defCall);
 * }
 * }</pre>
 */
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
   * <p>For calling methods annotated with @{@link kalix.javasdk.annotations.GenerateId}
   */
  WorkflowClient forWorkflow();

  /**
   * Select Workflow as a call target component.
   *
   * @param workflowId - workflow id used to create a call.
   */
  WorkflowClient forWorkflow(String workflowId);

  /**
   * Select Workflow as a call target component.
   *
   * @param workflowIds - compound workflow ids used to create a call.
   */
  WorkflowClient forWorkflow(String... workflowIds);

  /** Select View as a call target component. */
  ViewClient forView();
}
