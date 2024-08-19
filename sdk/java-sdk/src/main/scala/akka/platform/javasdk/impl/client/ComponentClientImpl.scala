/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl.client

import akka.annotation.InternalApi
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.client.ComponentClient
import akka.platform.javasdk.client.EventSourcedEntityClient
import akka.platform.javasdk.client.WorkflowClient
import akka.platform.javasdk.client.ActionClient
import akka.platform.javasdk.client.ViewClient
import akka.platform.javasdk.spi.{ ComponentClients => RuntimeComponentClients }

import scala.concurrent.ExecutionContext
import akka.platform.javasdk.client.KeyValueEntityClient
import akka.platform.javasdk.impl.MetadataImpl
import io.opentelemetry.api.trace.Span

/**
 * Note: new instance per call since it includes call metadata
 *
 * INTERNAL API
 */
@InternalApi
private[javasdk] final case class ComponentClientImpl(
    runtimeComponentClients: RuntimeComponentClients,
    openTelemetrySpan: Option[Span])(implicit ec: ExecutionContext)
    extends ComponentClient {

  // Volatile since the component client could be accessed in nested/composed futures and is mutated by the reflective action router
  @volatile var callMetadata: Option[Metadata] = openTelemetrySpan.map { span =>
    MetadataImpl.Empty.withTracing(span)
  }

  override def forAction(): ActionClient = ActionClientImpl(runtimeComponentClients.actionClient, callMetadata)

  override def forKeyValueEntity(valueEntityId: String): KeyValueEntityClient =
    new KeyValueEntityClientImpl(runtimeComponentClients.keyValueEntityClient, callMetadata, valueEntityId)

  override def forEventSourcedEntity(eventSourcedEntityId: String): EventSourcedEntityClient =
    EventSourcedEntityClientImpl(runtimeComponentClients.eventSourcedEntityClient, callMetadata, eventSourcedEntityId)

  override def forWorkflow(workflowId: String): WorkflowClient =
    WorkflowClientImpl(runtimeComponentClients.workFlowClient, callMetadata, workflowId)

  override def forView(): ViewClient = ViewClientImpl(runtimeComponentClients.viewClient, callMetadata)

}
