/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.telemetry

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Service
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.context.{ Context => OtelContext }
import kalix.protocol.action.ActionCommand
import kalix.protocol.component.MetadataEntry
import kalix.protocol.component.MetadataEntry.Value.StringValue
import kalix.protocol.component.{ Metadata => ProtocolMetadata }
import kalix.protocol.entity.Command
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang
import java.util.Collections
import scala.collection.mutable
import scala.jdk.OptionConverters._

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
case object ActionCategory extends ComponentCategory {
  def name = "Action"
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
private[akka] object Telemetry {

  val TRACE_PARENT_KEY: String = "traceparent"
  val TRACE_STATE_KEY: String = "tracestate"
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

  lazy val builderSetter: TextMapSetter[mutable.Builder[MetadataEntry, _]] = (carrier, key, value) => {
    carrier.addOne(new MetadataEntry(key, StringValue(value)))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object TraceInstrumentation {
  // Trick to extract trace parent from a single protocol metadata entry and using the W3C decoding from OTEL
  private val metadataEntryTraceParentGetter = new TextMapGetter[MetadataEntry]() {

    override def get(carrier: MetadataEntry, key: String): String =
      if (key == Telemetry.TRACE_PARENT_KEY) carrier.getStringValue
      else null

    override def keys(carrier: MetadataEntry): lang.Iterable[String] = Collections.singleton(Telemetry.TRACE_PARENT_KEY)
  }

  private val InstrumentationScopeName = "akka-javasdk"
}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] final class TraceInstrumentation(
    componentName: String,
    componentCategory: ComponentCategory,
    tracerFactory: String => Tracer) {

  import Telemetry._
  import TraceInstrumentation._

  private val propagator = ContextPropagators.create(W3CTraceContextPropagator.getInstance())
  private val traceNamePrefix = {
    // Note: aligned with runtime trace naming
    val simpleComponentName = componentName.split('.').last
    s"${componentCategory.name}: $simpleComponentName"
  }

  private val tracer = getTracer
  private val enabled = tracer != OpenTelemetry.noop().getTracer(InstrumentationScopeName)

  /**
   * Creates a span if it finds a trace parent in the command's metadata
   */
  def buildSpan(service: Service, command: Command): Option[Span] =
    if (enabled) internalBuildSpan(service, command.name, command.metadata, Some(command.entityId))
    else None

  /**
   * Creates a span if it finds a trace parent in the command's metadata
   */
  def buildSpan(service: Service, command: ActionCommand): Option[Span] =
    if (enabled) {
      val subject =
        command.metadata.flatMap(_.entries.find(_.key == MetadataImpl.CeSubject).flatMap(_.value.stringValue))
      internalBuildSpan(service, command.name, command.metadata, subject)
    } else None

  private def internalBuildSpan(
      service: Service,
      commandName: String,
      commandMetadata: Option[ProtocolMetadata],
      subjectId: Option[String]): Option[Span] = {
    // only if there is a trace parent in the metadata
    val traceParent = commandMetadata.flatMap(_.entries.find(_.key == TRACE_PARENT_KEY))
    traceParent.map { traceParentMetadataEntry =>
      val parentContext = propagator.getTextMapPropagator
        .extract(OtelContext.current(), traceParentMetadataEntry, metadataEntryTraceParentGetter)

      val spanName = s"$traceNamePrefix.${removeSyntheticName(commandName)}"
      var spanBuilder =
        getTracer
          .spanBuilder(spanName)
          .setParent(parentContext)
          .setSpanKind(SpanKind.SERVER)
          .setAttribute("component.type", service.componentType)
          .setAttribute("component.type_id", service.serviceName)
      subjectId.foreach(id => spanBuilder = spanBuilder.setAttribute("component.id", id))
      spanBuilder.startSpan()
    }
  }

  def getTracer: Tracer = tracerFactory(InstrumentationScopeName)

  private def removeSyntheticName(maybeSyntheticName: String): String =
    maybeSyntheticName
      .replace("KalixSyntheticMethodOnES", "")
      .replace("KalixSyntheticMethodOnTopic", "")
      .replace("KalixSyntheticMethodOnStream", "")
}
