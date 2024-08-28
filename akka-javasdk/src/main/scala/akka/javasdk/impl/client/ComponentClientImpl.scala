/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.client.ComponentClient
import akka.javasdk.client.EventSourcedEntityClient
import akka.javasdk.client.KeyValueEntityClient
import akka.javasdk.client.TimedActionClient
import akka.javasdk.client.ViewClient
import akka.javasdk.client.WorkflowClient
import akka.javasdk.impl.MetadataImpl
import akka.platform.javasdk.spi.{ ComponentClients => RuntimeComponentClients }

import scala.concurrent.ExecutionContext
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

  override def forTimedAction(): TimedActionClient =
    TimedActionClientImpl(runtimeComponentClients.actionClient, callMetadata)

  override def forKeyValueEntity(valueEntityId: String): KeyValueEntityClient =
    new KeyValueEntityClientImpl(runtimeComponentClients.keyValueEntityClient, callMetadata, valueEntityId)

  override def forEventSourcedEntity(eventSourcedEntityId: String): EventSourcedEntityClient =
    EventSourcedEntityClientImpl(runtimeComponentClients.eventSourcedEntityClient, callMetadata, eventSourcedEntityId)

  override def forWorkflow(workflowId: String): WorkflowClient =
    WorkflowClientImpl(runtimeComponentClients.workFlowClient, callMetadata, workflowId)

  override def forView(): ViewClient = ViewClientImpl(runtimeComponentClients.viewClient, callMetadata)

}
