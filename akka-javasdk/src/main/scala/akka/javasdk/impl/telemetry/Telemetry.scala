/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.telemetry

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.OptionConverters._

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.actor.typed.Extension
import akka.actor.typed.ExtensionId
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.ApplicationConfig
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.ProxyInfoHolder
import akka.javasdk.impl.Service
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.context.{ Context => OtelContext }
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.semconv.ServiceAttributes
import kalix.protocol.action.ActionCommand
import kalix.protocol.component.MetadataEntry
import kalix.protocol.component.MetadataEntry.Value.StringValue
import kalix.protocol.entity.Command
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
object Telemetry extends ExtensionId[Telemetry] {

  val TRACE_PARENT_KEY: String = TraceInstrumentation.TRACE_PARENT_KEY
  val TRACE_STATE_KEY: String = TraceInstrumentation.TRACE_STATE_KEY
  val TRACE_ID: String = "trace_id"

  override def createExtension(system: ActorSystem[_]): Telemetry =
    new Telemetry(system)
}

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
final class Telemetry(system: ActorSystem[_]) extends Extension {

  private val proxyInfoHolder = ProxyInfoHolder(system)

  private val logger = LoggerFactory.getLogger(classOf[Telemetry])

  private val applicationConfig = ApplicationConfig(system).getConfig

  private val collectorEndpointSDK = applicationConfig.getString(TraceInstrumentation.TRACING_ENDPOINT)

  implicit val ec: ExecutionContext = system.executionContext

  /**
   * This method assumes the instrumentation won't be consumed until discovery from the proxy is requested. Therefore
   * this should be stored in a `lazy` value and only used after we are sure the ProxyInfo has been process. For
   * example, in an Action inside the methods `handleyUnary` and alike.
   * @param componentName
   * @param componentCategory
   * @return
   */
  def traceInstrumentation(componentName: String, componentCategory: ComponentCategory): Instrumentation = {
    val collectorEndpoint = {
      if (collectorEndpointSDK.nonEmpty) collectorEndpointSDK
      else
        proxyInfoHolder.proxyTracingCollectorEndpoint match {
          case Some(endpoint) => endpoint
          case None => throw new IllegalArgumentException("Tracing endpoint from the Proxy not yet received. Retry.")
        }
    }
    if (collectorEndpoint.isEmpty) {
      logger.debug("Instrumentation disabled. Set to NoOp.")
      NoOpInstrumentation
    } else {
      logger.debug("Instrumentation enabled. Set collector endpoint to [{}].", collectorEndpoint)
      new TraceInstrumentation(collectorEndpoint, componentName, system, componentCategory)
    }
  }
}

/**
 * INTERNAL API
 */
@InternalApi
trait Instrumentation {

  def buildSpan(service: Service, command: Command): Option[Span]

  def buildSpan(service: Service, command: ActionCommand): Option[Span]

  def getTracer: Tracer

}

/**
 * INTERNAL API
 */
@InternalApi
private[akka] object TraceInstrumentation {

  val TRACE_PARENT_KEY = "traceparent"
  val TRACE_STATE_KEY = "tracestate"

  // FIXME strange having both this and kalix.proxy.telemetry.tracing.collector-endpoint now with embedded SDK
  val TRACING_ENDPOINT = "akka.javasdk.telemetry.tracing.collector-endpoint"

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  lazy val metadataGetter = new TextMapGetter[Metadata]() {
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
private final class TraceInstrumentation(
    collectorEndpoint: String,
    componentName: String,
    system: ActorSystem[_],
    componentCategory: ComponentCategory)
    extends Instrumentation {

  import TraceInstrumentation._

  private val tracePrefix = componentCategory.name

  private val openTelemetry: OpenTelemetry = {
    val resource =
      Resource.getDefault.merge(
        Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, s"$tracePrefix($componentName)")))
    val sdkTracerProvider = SdkTracerProvider
      .builder()
      .addSpanProcessor(
        SimpleSpanProcessor.create(
          OtlpGrpcSpanExporter
            .builder()
            .setEndpoint(collectorEndpoint)
            .build()))
      .setResource(resource)
      .build()

    val sdk = OpenTelemetrySdk
      .builder()
      .setTracerProvider(sdkTracerProvider)
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .build()
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "close telemetry") { () =>
      sdk.close()
      Future.successful(Done)
    }
    sdk
  }

  /**
   * Creates a span if it finds a trace parent in the command's metadata
   * @param service
   * @param command
   * @return
   */
  override def buildSpan(service: Service, command: Command): Option[Span] = {
    if (logger.isTraceEnabled) logger.trace("Building span for command [{}].", command)
    val metadata = MetadataImpl.of(command.metadata.map(_.entries).getOrElse(Nil))
    if (metadata.get(TRACE_PARENT_KEY).isPresent) {
      if (logger.isTraceEnabled) logger.trace("`traceparent` found")

      val context = openTelemetry.getPropagators.getTextMapPropagator
        .extract(OtelContext.current(), metadata, metadataGetter)

      val span = openTelemetry
        .getTracer("java-sdk")
        .spanBuilder(command.name)
        .setParent(context)
        .setSpanKind(SpanKind.SERVER)
        .startSpan()
      Some(
        span
          .setAttribute("component.type", service.componentType)
          .setAttribute("component.type_id", service.serviceName)
          .setAttribute("component.id", command.entityId))
    } else {
      if (logger.isTraceEnabled) logger.trace("No `traceparent` found for command [{}].", command)
      None
    }
  }

  override def buildSpan(service: Service, command: ActionCommand): Option[Span] = {
    if (logger.isTraceEnabled) logger.trace("Building span for action command [{}].", command)

    val metadata = MetadataImpl.of(command.metadata.map(_.entries).getOrElse(Nil))
    if (metadata.get(TRACE_PARENT_KEY).isPresent) {
      if (logger.isTraceEnabled) logger.trace("`traceparent` found")

      val context = openTelemetry.getPropagators.getTextMapPropagator
        .extract(OtelContext.current(), metadata, metadataGetter)

      val span = getTracer
        .spanBuilder(command.name)
        .setParent(context)
        .setSpanKind(SpanKind.SERVER)
        .startSpan()
      Some(
        span
          .setAttribute("service.name", service.serviceName)
          .setAttribute("component.type", service.componentType))
    } else {
      if (logger.isTraceEnabled) logger.trace("No `traceparent` found for command [{}].", command)
      None
    }
  }

  // TODO: should this be specific per sdk?
  override def getTracer: Tracer = openTelemetry.getTracer("kalix")
}

/**
 * INTERNAL API
 */
@InternalApi
private object NoOpInstrumentation extends Instrumentation {

  override def buildSpan(service: Service, command: Command): Option[Span] = None

  override def buildSpan(service: Service, command: ActionCommand): Option[Span] = None

  override def getTracer: Tracer = OpenTelemetry.noop().getTracer("noop")
}
