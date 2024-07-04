/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.client

import akka.annotation.InternalApi
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.client.ComponentClient
import akka.platform.javasdk.client.EventSourcedEntityClient
import akka.platform.javasdk.client.ValueEntityClient
import akka.platform.javasdk.client.WorkflowClient
import akka.platform.javasdk.client.ActionClient
import akka.platform.javasdk.client.ViewClient
import kalix.javasdk.spi.{ ComponentClients => RuntimeComponentClients }

import scala.concurrent.ExecutionContext

/**
 * Note: new instance per call since it includes call metadata
 *
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ComponentClientImpl(runtimeComponentClients: RuntimeComponentClients)(implicit
    ec: ExecutionContext)
    extends ComponentClient {

  // Volatile since the component client could be accessed in nested/composed futures
  @volatile var callMetadata: Option[Metadata] = None

  override def forAction(): ActionClient = ActionClientImpl(runtimeComponentClients.actionClient, callMetadata)

  override def forValueEntity(valueEntityId: String): ValueEntityClient =
    new ValueEntityClientImpl(runtimeComponentClients.valueEntityClient, callMetadata, valueEntityId)

  override def forEventSourcedEntity(eventSourcedEntityId: String): EventSourcedEntityClient =
    EventSourcedEntityClientImpl(runtimeComponentClients.eventSourcedEntityClient, callMetadata, eventSourcedEntityId)

  override def forWorkflow(workflowId: String): WorkflowClient =
    WorkflowClientImpl(runtimeComponentClients.workFlowClient, callMetadata, workflowId)

  override def forView(): ViewClient = ViewClientImpl(runtimeComponentClients.viewClient, callMetadata)

}
