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

  private val componentDescriptor = ComponentDescriptor(
    Map(
      BytesPrimitive.fullName -> MethodInvoker(classOf[ThrowingConsumer].getMethods.find(_.getName == "onBytes").get)))

  def create(unhandledExceptionReporter: () => Option[UnhandledExceptionReporting.Reporter] = () => None)
      : ConsumerImpl[ThrowingConsumer] =
    new ConsumerImpl(
      "throwing-consumer",
      _ => new ThrowingConsumer,
      classOf[ThrowingConsumer],
      new ConsumerSource.EventSourcedEntitySource("dummy-source", startFromSnapshots = false),
      None,
      classicSystem,
      timerClient,
      classicSystem.dispatcher,
      () => OpenTelemetry.noop().getTracer("test"),
      serializer,
      ignoreUnknown = false,
      componentDescriptor,
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

      val reply =
        LoggingTestKit
          .error("Failure during handling message")
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
