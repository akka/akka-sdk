/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.timedaction

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.Done
import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.javasdk.annotations.ComponentId
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.TimedActionDescriptorFactory
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.timedaction.TimedAction
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.DeferredRequest
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiTimedAction
import akka.runtime.sdk.spi.TimerClient
import akka.util.ByteString
import io.opentelemetry.api.OpenTelemetry
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

  private val serializer = new JsonSerializer
  private val timerClient = new TimerClient {
    // Not exercised here
    override def startSingleTimer(
        name: String,
        delay: FiniteDuration,
        maxRetries: Int,
        deferredRequest: DeferredRequest): Future[Done] = ???
    override def removeTimer(name: String): Future[Done] = ???
  }

  def create(componentDescriptor: ComponentDescriptor): TimedActionImpl[TestTimedAction] = {
    new TimedActionImpl(
      "dummy-id",
      () => new TestTimedAction,
      classOf[TestTimedAction],
      classicSystem,
      timerClient,
      classicSystem.dispatcher,
      () => OpenTelemetry.noop().getTracer("test"),
      serializer,
      new RegionInfo(""),
      componentDescriptor)
  }

  @ComponentId("dummy-id")
  class TestTimedAction extends TimedAction {

    def myMethod(): TimedAction.Effect = {
      effects().done()
    }

    def myMethodWithException(): TimedAction.Effect = {
      throw new IllegalStateException("boom")
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

  }
}
