/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.runtime.sdk.spi.UserFunctionError
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.event.Level

class SdkRunnerReportErrorSpec extends ScalaTestWithActorTestKit with LogCapturing with AnyWordSpecLike {

  private val spanId = "b7ad6b7169203331"
  private val telemetryContext = Context
    .root()
    .`with`(
      Span.wrap(
        SpanContext.create("0af7651916cd43dd8448eb211c80319c", spanId, TraceFlags.getSampled, TraceState.getDefault)))

  "Reporting an error from the runtime" should {

    "log with the correlation id in the MDC and the telemetry context current" in {
      val error =
        new UserFunctionError(
          "Consumer is failing",
          "AK-00433",
          "",
          Level.WARN,
          Some("correlation-1"),
          telemetryContext)

      // the predicate runs on the logging thread, so it sees the current span
      var currentSpanId: Option[String] = None
      LoggingTestKit
        .custom { event =>
          val matches = event.message.contains("AK-00433") &&
            event.mdc.get(ErrorHandling.CorrelationIdMdcKey).contains("correlation-1")
          if (matches) currentSpanId = Some(Span.current().getSpanContext.getSpanId)
          matches
        }
        .expect {
          SdkRunner.logReportedError(error)
        }
      currentSpanId shouldBe Some(spanId)
    }

    "log without correlation id or telemetry context" in {
      val error = new UserFunctionError("Consumer is failing", "AK-00433", "", Level.WARN)

      LoggingTestKit
        .custom { event =>
          event.message.contains("AK-00433") && !event.mdc.contains(ErrorHandling.CorrelationIdMdcKey)
        }
        .expect {
          SdkRunner.logReportedError(error)
        }
    }
  }
}
