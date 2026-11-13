/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.telemetry

import scala.collection.mutable
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.runtime.sdk.spi.SpiMetadataEntry
import io.opentelemetry.context.{ Context => OtelContext }
import io.opentelemetry.context.Scope
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

  /**
   * Makes the context the current OpenTelemetry context of the calling thread, until the returned scope is closed. The
   * scope must be closed on the same thread.
   */
  def makeCurrent(context: Option[OtelContext]): Scope =
    context match {
      case Some(ctx) => ctx.makeCurrent()
      case None      => Scope.noop()
    }

  /** Runs the block with the context as the current OpenTelemetry context, on the calling thread only. */
  def withCurrent[T](context: Option[OtelContext])(block: => T): T = {
    val scope = makeCurrent(context)
    try block
    finally scope.close()
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
  val InstrumentationScopeName: String = "akka-javasdk"
}
