/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.telemetry

import java.lang
import java.util.Locale

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiMetadataEntry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.context.{ Context => OtelContext }
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
sealed trait ComponentCategory {
  def name: String
}

/**
 * INTERNAL API
 */
@InternalApi
case object ConsumerCategory extends ComponentCategory {
  def name = "Consumer"
}

/**
 * INTERNAL API
 */
@InternalApi
case object TimedActionCategory extends ComponentCategory {
  def name = "TimedAction"
}

/**
 * INTERNAL API
 */
@InternalApi
case object EventSourcedEntityCategory extends ComponentCategory {
  def name = "Event Sourced Entity"
}

/**
 * INTERNAL API
 */
@InternalApi
case object KeyValueEntityCategory extends ComponentCategory {
  def name = "Key Value Entity"
}

/**
 * INTERNAL API
 */
@InternalApi
case object AgentCategory extends ComponentCategory {
  def name = "Agent"
}

/**
 * INTERNAL API
 */
@InternalApi
case object WorkflowCategory extends ComponentCategory {
  def name = "Workflow"
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object Telemetry {

  val TRACE_ID: String = "trace_id"

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  lazy val metadataGetter: TextMapGetter[Metadata] = new TextMapGetter[Metadata]() {
    override def get(carrier: Metadata, key: String): String = {
      if (logger.isTraceEnabled) logger.trace("For the key [{}] the value is [{}]", key, carrier.get(key))
      carrier.get(key).toScala.getOrElse("")
    }

    override def keys(carrier: Metadata): java.lang.Iterable[String] =
      carrier.getAllKeys
  }

  lazy val builderSetter: TextMapSetter[mutable.Builder[SpiMetadataEntry, _]] = (carrier, key, value) => {
    carrier.addOne(new SpiMetadataEntry(key, value))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object TraceInstrumentation {
  private val spiMetadataGetter = new TextMapGetter[SpiMetadata]() {
    override def get(metadata: SpiMetadata, key: String): String = {
      metadata.entries.find(entry => lowerCase(entry.key) == lowerCase(key)).map(_.value).orNull
    }

    override def keys(metadata: SpiMetadata): lang.Iterable[String] = {
      metadata.entries.map(_.key).asJava
    }

    private def lowerCase(key: String): String = {
      key.toLowerCase(Locale.ROOT)
    }
  }

  val InstrumentationScopeName: String = "akka-javasdk"
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class TraceInstrumentation(
    componentName: String,
    componentCategory: ComponentCategory,
    val tracerFactory: () => Tracer) {

  import TraceInstrumentation._

  private val propagator = ContextPropagators.create(W3CTraceContextPropagator.getInstance())
  private val traceNamePrefix = {
    // Note: aligned with runtime trace naming
    val simpleComponentName = componentName.split('.').last
    s"${componentCategory.name}: $simpleComponentName"
  }

  private val tracer = tracerFactory()
  private val enabled = tracer != OpenTelemetry.noop().getTracer(InstrumentationScopeName)

  /**
   * Creates a span if tracing enabled and it finds a trace parent in the command's metadata
   */
  def buildEntityCommandSpan(
      componentType: String,
      componentId: String,
      entityId: String,
      command: SpiEntity.Command): Option[Span] =
    buildEntityCommandSpan(componentType, componentId, entityId, command.name, command.metadata)

  /**
   * Creates a span if tracing enabled, and it finds a trace parent in the command's metadata
   */
  def buildEntityCommandSpan(
      componentType: String,
      componentId: String,
      entityId: String,
      commandName: String,
      metadata: SpiMetadata): Option[Span] =
    if (enabled) internalBuildSpan(componentType, componentId, Some(commandName), metadata, Some(entityId))
    else None

  /**
   * Creates a span if tracing enabled, and if it finds a trace parent in the command's metadata
   */
  def buildSpan(
      componentType: String,
      componentId: String,
      subjectId: Option[String],
      spiMetadata: SpiMetadata): Option[Span] =
    if (enabled) internalBuildSpan(componentType, componentId, None, spiMetadata, subjectId)
    else None

  private def internalBuildSpan(
      componentType: String,
      componentId: String,
      commandName: Option[String],
      commandMetadata: SpiMetadata,
      subjectId: Option[String]): Option[Span] = {
    val context = propagator.getTextMapPropagator.extract(OtelContext.root, commandMetadata, spiMetadataGetter)
    val parentSpan = Option(Span.fromContextOrNull(context))
    parentSpan.map { _ =>
      val spanName = s"$traceNamePrefix${commandName.fold("")("." + _)}"
      var spanBuilder =
        tracer
          .spanBuilder(spanName)
          .setParent(context)
          .setSpanKind(SpanKind.SERVER)
          .setAttribute("component.type", componentType)
          .setAttribute("component.type_id", componentId)
      subjectId.foreach(id => spanBuilder = spanBuilder.setAttribute("component.id", id))
      spanBuilder.startSpan()
    }
  }

}
