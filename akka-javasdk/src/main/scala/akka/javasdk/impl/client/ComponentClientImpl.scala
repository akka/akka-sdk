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
import akka.runtime.sdk.spi.{ ComponentClients => RuntimeComponentClients }

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
    if (valueEntityId eq null) throw new NullPointerException("Key Value entity id is null")
    else if (valueEntityId.isEmpty) throw new IllegalArgumentException("Empty value entity id now allowed")
    else new KeyValueEntityClientImpl(runtimeComponentClients.keyValueEntityClient, callMetadata, valueEntityId)

  override def forEventSourcedEntity(eventSourcedEntityId: String): EventSourcedEntityClient =
    if (eventSourcedEntityId eq null) throw new NullPointerException("Event sourced entity id is null")
    else if (eventSourcedEntityId.isEmpty)
      throw new IllegalArgumentException("Empty event sourced entity id now allowed")
    else
      EventSourcedEntityClientImpl(runtimeComponentClients.eventSourcedEntityClient, callMetadata, eventSourcedEntityId)

  override def forWorkflow(workflowId: String): WorkflowClient =
    if (workflowId eq null) throw new NullPointerException("Workflow id is null")
    else if (workflowId.isEmpty) throw new IllegalArgumentException("Empty workflow id now allowed")
    else WorkflowClientImpl(runtimeComponentClients.workFlowClient, callMetadata, workflowId)

  override def forView(): ViewClient = ViewClientImpl(runtimeComponentClients.viewClient, callMetadata)

}
