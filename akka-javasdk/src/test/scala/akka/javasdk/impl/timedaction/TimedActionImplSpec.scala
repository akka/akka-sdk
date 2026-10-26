/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.Done
import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.javasdk.annotations.Component
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.TimedActionDescriptorFactory
import akka.javasdk.impl.UnhandledExceptionReporting
import akka.javasdk.impl.serialization.Serializer
import akka.javasdk.timedaction.TimedAction
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.DeferredRequest
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiTimedAction
import akka.runtime.sdk.spi.SpiUnhandledException
import akka.runtime.sdk.spi.TimerClient
import akka.util.ByteString
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class TimedActionImplSpec
    extends ScalaTestWithActorTestKit
    with LogCapturing
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside
    with OptionValues {

  private val classicSystem = system.toClassic

  // span id the command handler saw as current span
  private val currentSpanIdInHandler = new AtomicReference[String]()

  private val serializer = new Serializer
  private val timerClient = new TimerClient {
    // Not exercised here
    override def startSingleTimer(
        name: String,
        delay: FiniteDuration,
        maxRetries: Int,
        deferredRequest: DeferredRequest): Future[Done] = ???
    override def removeTimer(name: String): Future[Done] = ???

    override def isTimerActive(name: String): Future[Boolean] = ???
  }

  def create(
      componentDescriptor: ComponentDescriptor,
      unhandledExceptionReporter: () => Option[UnhandledExceptionReporting.Reporter] = () => None)
      : TimedActionImpl[TestTimedAction] = {
    new TimedActionImpl(
      "dummy-id",
      _ => new TestTimedAction,
      classOf[TestTimedAction],
      classicSystem,
      timerClient,
      classicSystem.dispatcher,
      () => OpenTelemetry.noop().getTracer("test"),
      serializer,
      new RegionInfo(""),
      componentDescriptor,
      unhandledExceptionReporter)
  }

  @Component(id = "dummy-id")
  class TestTimedAction extends TimedAction {

    def myMethod(): TimedAction.Effect = {
      effects().done()
    }

    def myMethodWithException(): TimedAction.Effect = {
      throw new IllegalStateException("boom")
    }

    def myMethodReturningNull(): TimedAction.Effect = null

    def myMethodRecordingSpan(): TimedAction.Effect = {
      currentSpanIdInHandler.set(Span.current().getSpanContext.getSpanId)
      effects().done()
    }

  }

  "The action service" should {
    "invoke command handler" in {
      val service = create(TimedActionDescriptorFactory.buildDescriptorFor(classOf[TestTimedAction], serializer))

      val reply: SpiTimedAction.Effect =
        service
          .handleCommand(new SpiTimedAction.Command("MyMethod", Some(BytesPayload.empty), SpiMetadata.empty))
          .futureValue

      reply shouldBe an[SpiTimedAction.SuccessEffect.type]
    }

    "run the command handler with the command telemetry context current" in {
      val service = create(TimedActionDescriptorFactory.buildDescriptorFor(classOf[TestTimedAction], serializer))
      val spanId = "b7ad6b7169203331"
      val span = Span.wrap(
        SpanContext.create("0af7651916cd43dd8448eb211c80319c", spanId, TraceFlags.getSampled, TraceState.getDefault))

      service
        .handleCommand(
          new SpiTimedAction.Command(
            "MyMethodRecordingSpan",
            Some(BytesPayload.empty),
            SpiMetadata.empty,
            Context.root().`with`(span)))
        .futureValue shouldBe an[SpiTimedAction.SuccessEffect.type]

      currentSpanIdInHandler.get shouldBe spanId
      // the scope is closed after the handler
      Span.current().getSpanContext.isValid shouldBe false
    }

    "turn thrown command handler exceptions into failure responses" in {
      val service = create(TimedActionDescriptorFactory.buildDescriptorFor(classOf[TestTimedAction], serializer))

      val reply =
        LoggingTestKit
          .error("Failure during handling command [MyMethodWithException] from TimedAction component [TestTimedAction]")
          .expect {
            service
              .handleCommand(
                new SpiTimedAction.Command(
                  "MyMethodWithException",
                  Some(new BytesPayload(ByteString.empty, "")),
                  SpiMetadata.empty))
              .futureValue
              .asInstanceOf[SpiTimedAction.ErrorEffect]
          }

      reply.error.description should startWith("Unexpected error")
    }

    "turn a null Effect from the command handler into a failure response with a clear message" in {
      val service = create(TimedActionDescriptorFactory.buildDescriptorFor(classOf[TestTimedAction], serializer))

      val reply =
        LoggingTestKit
          .custom { event =>
            event.message.startsWith("Failure during handling command [MyMethodReturningNull]") &&
            event.throwable.exists(
              _.getMessage.startsWith("TimedAction [dummy-id] command handler [MyMethodReturningNull] returned null"))
          }
          .expect {
            service
              .handleCommand(
                new SpiTimedAction.Command("MyMethodReturningNull", Some(BytesPayload.empty), SpiMetadata.empty))
              .futureValue
              .asInstanceOf[SpiTimedAction.ErrorEffect]
          }

      reply.error.description should startWith("Unexpected error")
    }

    "report an unhandled exception thrown by the command handler to the unhandled exception reporter" in {
      val reported = new java.util.concurrent.CompletableFuture[SpiUnhandledException]()
      val reporter: UnhandledExceptionReporting.Reporter = { spiEx =>
        reported.complete(spiEx)
        Future.successful(Done)
      }
      val service =
        create(
          TimedActionDescriptorFactory.buildDescriptorFor(classOf[TestTimedAction], serializer),
          () => Some(reporter))

      LoggingTestKit
        .error("Failure during handling command [MyMethodWithException] from TimedAction component [TestTimedAction]")
        .expect {
          service
            .handleCommand(
              new SpiTimedAction.Command(
                "MyMethodWithException",
                Some(new BytesPayload(ByteString.empty, "")),
                SpiMetadata.empty))
            .futureValue
        }

      val spiException = reported.get(5, java.util.concurrent.TimeUnit.SECONDS)
      spiException.throwable.getMessage should include("MyMethodWithException")
      spiException.componentId shouldBe "dummy-id"
      spiException.componentClassName shouldBe classOf[TestTimedAction].getName
    }

  }
}
