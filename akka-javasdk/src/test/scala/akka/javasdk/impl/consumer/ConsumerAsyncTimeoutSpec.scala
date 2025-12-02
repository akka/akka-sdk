/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.annotations.Component
import akka.javasdk.consumer.Consumer
import akka.javasdk.impl.ConsumerDescriptorFactory
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.ConsumerSource.TopicSource
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiConsumer
import akka.runtime.sdk.spi.SpiConsumer.Message
import akka.runtime.sdk.spi.SpiMetadata
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

object ConsumerAsyncTimeoutSpec {
  case class Event()

  @Component(id = "dummy")
  private class BuggyConsumer extends Consumer {
    def onEvent(event: Event): Consumer.Effect = {
      effects().asyncEffect(new CompletableFuture[Consumer.Effect]())
    }
  }
}

class ConsumerAsyncTimeoutSpec
    extends ScalaTestWithActorTestKit("akka.javasdk.consumer.async-result-timeout=50ms")
    with AnyWordSpecLike
    with Matchers
    with LogCapturing {
  import ConsumerAsyncTimeoutSpec._

  "The ConsumerImpl" should {

    "fail an async consumer effect that takes too much time" in {
      val jsonSerializer = new JsonSerializer()
      val consumerImpl = new ConsumerImpl[BuggyConsumer](
        "dummy",
        _ => new BuggyConsumer,
        classOf[BuggyConsumer],
        new TopicSource("dummmy", ""),
        None,
        system.classicSystem,
        null,
        system.executionContext,
        () => null,
        jsonSerializer,
        false,
        ConsumerDescriptorFactory.buildDescriptorFor(classOf[BuggyConsumer], jsonSerializer),
        new RegionInfo("us-east1"))

      val result =
        LoggingTestKit.error[TimeoutException].expect {
          consumerImpl
            .handleMessage(new Message(Some(jsonSerializer.toBytes(Event())), None, SpiMetadata.empty, null))
            .futureValue
        }
      result shouldBe a[SpiConsumer.ErrorEffect]
      result.asInstanceOf[SpiConsumer.ErrorEffect].error.description should include("Unexpected error")

    }

  }

}
