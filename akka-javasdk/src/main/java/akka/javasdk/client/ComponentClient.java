/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.agent.Agent;

/**
 * Utility to send requests to other components by composing a call that can be executed by the
 * runtime. To compose a call:
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
 * public CompletionStage<Done> addItem(String cartId, ShoppingCart.LineItem item) {
 *   return componentClient.forEventSourcedEntity(cartId)
 *     .method(ShoppingCartEntity::addItem)
 *     .invokeAsync(item);
 * }
 * }</pre>
 *
 * Not for user extension, implementation provided by the SDK.
 */
@DoNotInherit
public interface ComponentClient {
  /** Select {@link akka.javasdk.timedaction.TimedAction} as a call target component. */
  TimedActionClient forTimedAction();

  /**
   * Select {@link akka.javasdk.keyvalueentity.KeyValueEntity} as a call target component.
   *
   * @param keyValueEntityId - key value entity id used to create a call. Must not be null or empty
   *     string.
   */
  KeyValueEntityClient forKeyValueEntity(String keyValueEntityId);

  /**
   * Select {@link akka.javasdk.eventsourcedentity.EventSourcedEntity} as a call target component.
   *
   * @param eventSourcedEntityId - event sourced entity id used to create a call. Must not be null
   *     or empty string.
   */
  EventSourcedEntityClient forEventSourcedEntity(String eventSourcedEntityId);

  /**
   * Select {@link akka.javasdk.workflow.Workflow} as a call target component.
   *
   * @param workflowId - workflow id used to create a call. Must not be null or empty string.
   */
  WorkflowClient forWorkflow(String workflowId);

  /** Select {@link akka.javasdk.view.View} as a call target component. */
  ViewClient forView();

  /**
   * Select {@link Agent} as a call target component.
   */
  AgentClient forAgent();

}
