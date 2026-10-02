/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.javasdk.Metadata
import akka.runtime.sdk.spi.SpiMetadataEntry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.{ Context => OtelContext }
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MetadataImplSpec extends AnyWordSpec with Matchers with OptionValues {

  "MetadataImpl" should {

    "support creating with CloudEvents prefixed with ce_" in {
      val md = metadata("ce_id" -> "id", "ce_source" -> "source", "ce_specversion" -> "1.0", "ce_type" -> "foo")
      md.isCloudEvent shouldBe true
      val ce = md.asCloudEvent()
      ce.id() shouldBe "id"
      ce.source().toString shouldBe "source"
      ce.specversion() shouldBe "1.0"
      ce.`type`() shouldBe "foo"
    }

    "metadata should be mergeable" in {
      val md1 = metadata("foo" -> "bar", "foobar" -> "raboof")
      val md2 = metadata("baz" -> "qux", "foobar" -> "foobar")
      val merged = md1.merge(md2)
      merged.get("foo").toScala.value shouldBe "bar"
      merged.get("baz").toScala.value shouldBe "qux"

      val expectedEntries = "raboof" :: "foobar" :: Nil
      merged.getAll("foobar").asScala shouldBe expectedEntries
      merged.get("foobar").toScala.value shouldBe "raboof" // first
      merged.getLast("foobar").toScala.value shouldBe "foobar"
    }

    "carry the trace context and the baggage of a telemetry context" in {
      val spanContext = SpanContext.create(
        "4bf92f3577b34da6a3ce929d0e0e4736",
        "00f067aa0ba902b7",
        TraceFlags.getSampled,
        TraceState.getDefault)
      val context = OtelContext
        .root()
        .`with`(Span.wrap(spanContext))
        .`with`(Baggage.builder().put("akka.evaluation.id", "evaluation-1").build())

      val withContext = MetadataImpl.Empty.withTelemetryContext(context)

      withContext.get("traceparent").toScala.value should include(spanContext.getTraceId)
      withContext.get("baggage").toScala.value shouldBe "akka.evaluation.id=evaluation-1"
    }

    "carry the baggage of a telemetry context without a span" in {
      val context = OtelContext.root().`with`(Baggage.builder().put("akka.evaluation.id", "evaluation-1").build())

      val withContext = MetadataImpl.Empty.withTelemetryContext(context)

      withContext.get("traceparent").toScala shouldBe None
      withContext.get("baggage").toScala.value shouldBe "akka.evaluation.id=evaluation-1"
    }
  }

  private def metadata(entries: (String, String)*): Metadata = {
    MetadataImpl.of(entries.map { case (key, value) =>
      new SpiMetadataEntry(key, value)
    })
  }

}
