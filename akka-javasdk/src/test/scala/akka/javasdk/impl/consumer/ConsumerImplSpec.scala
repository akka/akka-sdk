/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.Done
import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.javasdk.consumer.Consumer
import akka.javasdk.impl.AnySupport.BytesPrimitive
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.UnhandledExceptionReporting
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.ConsumerSource
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiConsumer
import akka.runtime.sdk.spi.SpiUnhandledException
import akka.runtime.sdk.spi.TimerClient
import akka.util.ByteString
import io.opentelemetry.api.OpenTelemetry
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ConsumerImplSpec
    extends ScalaTestWithActorTestKit
    with LogCapturing
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  private val classicSystem = system.toClassic

  private val serializer = new Serializer
  private val timerClient = new TimerClient {
    override def startSingleTimer(
        name: String,
        delay: FiniteDuration,
        maxRetries: Int,
        deferredRequest: akka.runtime.sdk.spi.DeferredRequest): Future[Done] = ???
    override def removeTimer(name: String): Future[Done] = ???
    override def isTimerActive(name: String): Future[Boolean] = ???
  }

  final class ThrowingConsumer extends Consumer {
    def onBytes(bytes: Array[Byte]): Consumer.Effect =
      throw new IllegalStateException("spike-16304: deliberate exception from consumer onEvent")
  }

  final class NullReturningConsumer extends Consumer {
    def onBytes(bytes: Array[Byte]): Consumer.Effect = null
  }

  private def bytesDescriptor(consumerClass: Class[_ <: Consumer]) = ComponentDescriptor(
    Map(BytesPrimitive.fullName -> MethodInvoker(consumerClass.getMethods.find(_.getName == "onBytes").get)))

  def create(unhandledExceptionReporter: () => Option[UnhandledExceptionReporting.Reporter] = () => None)
      : ConsumerImpl[ThrowingConsumer] =
    create("throwing-consumer", classOf[ThrowingConsumer], () => new ThrowingConsumer, unhandledExceptionReporter)

  def create[C <: Consumer](
      componentId: String,
      consumerClass: Class[C],
      newConsumer: () => C,
      unhandledExceptionReporter: () => Option[UnhandledExceptionReporting.Reporter]): ConsumerImpl[C] =
    new ConsumerImpl(
      componentId,
      _ => newConsumer(),
      consumerClass,
      new ConsumerSource.EventSourcedEntitySource("dummy-source", startFromSnapshots = false),
      None,
      classicSystem,
      timerClient,
      classicSystem.dispatcher,
      () => OpenTelemetry.noop().getTracer("test"),
      serializer,
      ignoreUnknown = false,
      bytesDescriptor(consumerClass),
      new RegionInfo(""),
      unhandledExceptionReporter)

  private def throwingMessage = new SpiConsumer.Message(
    Some(new BytesPayload(ByteString("some bytes"), BytesPrimitive.fullName)),
    None,
    akka.runtime.sdk.spi.SpiMetadata.empty,
    io.opentelemetry.context.Context.root())

  "The consumer service" should {

    "turn thrown command handler exceptions into failure responses" in {
      val service = create()

      var loggedCorrelationId: Option[String] = None
      val reply =
        LoggingTestKit
          .custom { event =>
            val matches = event.message.startsWith("Failure during handling message")
            if (matches) loggedCorrelationId = event.mdc.get(ErrorHandling.CorrelationIdMdcKey)
            matches
          }
          .expect {
            service.handleMessage(throwingMessage).futureValue.asInstanceOf[SpiConsumer.ErrorEffect]
          }

      reply.error.description should startWith("Unexpected error")
      loggedCorrelationId should not be empty
      reply.error.correlationId shouldBe loggedCorrelationId
    }

    "turn a null Effect from the handler into a failure response with a clear message" in {
      val service = create("null-consumer", classOf[NullReturningConsumer], () => new NullReturningConsumer, () => None)

      val reply =
        LoggingTestKit
          .custom { event =>
            event.message.startsWith("Failure during handling message") &&
            event.throwable.exists(_.getMessage.startsWith("Consumer [null-consumer] handler returned null"))
          }
          .expect {
            service.handleMessage(throwingMessage).futureValue.asInstanceOf[SpiConsumer.ErrorEffect]
          }

      reply.error.description should startWith("Unexpected error")
    }

    "report an unhandled exception thrown by the user's onEvent method to the unhandled exception reporter" in {
      val reported = new java.util.concurrent.CompletableFuture[SpiUnhandledException]()
      val reporter: UnhandledExceptionReporting.Reporter = { spiEx =>
        reported.complete(spiEx)
        Future.successful(Done)
      }
      val service = create(() => Some(reporter))

      LoggingTestKit.error("Failure during handling message").expect {
        service.handleMessage(throwingMessage).futureValue
      }

      val spiException = reported.get(5, java.util.concurrent.TimeUnit.SECONDS)
      spiException.throwable.getMessage should include("spike-16304")
      spiException.componentId shouldBe "throwing-consumer"
      spiException.componentClassName shouldBe classOf[ThrowingConsumer].getName
    }
  }
}
