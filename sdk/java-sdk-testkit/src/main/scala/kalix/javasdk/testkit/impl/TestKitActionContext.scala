/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.testkit.impl;

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import akka.platform.javasdk.Metadata
import akka.platform.javasdk.action.{ ActionContext, ActionCreationContext }
import akka.platform.javasdk.impl.InternalContext
import akka.platform.javasdk.testkit.MockRegistry

import java.util.Optional

/**
 * INTERNAL API Used by the generated testkit
 */
final class TestKitActionContext(metadata: Metadata, mockRegistry: MockRegistry = MockRegistry.EMPTY)
    extends AbstractTestKitContext(mockRegistry)
    with ActionContext
    with ActionCreationContext
    with InternalContext {

  def this() = {
    this(Metadata.EMPTY, MockRegistry.EMPTY)
  }

  def this(metadata: Metadata) = {
    this(metadata, MockRegistry.EMPTY)
  }

  override def metadata() = metadata

  override def eventSubject() = metadata.get("ce-subject")

  override def getGrpcClient[T](clientClass: Class[T], service: String): T = getComponentGrpcClient(clientClass)

  override def getOpenTelemetryTracer: Optional[Tracer] = Optional.empty()

  override def getTracer: Tracer = OpenTelemetry.noop().getTracer("noop")
}
