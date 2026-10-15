/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

import scala.reflect.ClassTag

import akka.javasdk.agent.MessageContent.ImageMessageContent
import akka.runtime.sdk.spi.SpiAgent
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

class AgentImplUnknownValuesSpec extends AnyWordSpec with Matchers with TableDrivenPropertyChecks {

  private val detailLevels = Table[SpiAgent.ImageMessageContent.DetailLevel, ImageMessageContent.DetailLevel](
    ("spi", "public"),
    (SpiAgent.ImageMessageContent.Low, ImageMessageContent.DetailLevel.LOW),
    (SpiAgent.ImageMessageContent.Medium, ImageMessageContent.DetailLevel.MEDIUM),
    (SpiAgent.ImageMessageContent.High, ImageMessageContent.DetailLevel.HIGH),
    (SpiAgent.ImageMessageContent.UltraHigh, ImageMessageContent.DetailLevel.ULTRA_HIGH),
    (SpiAgent.ImageMessageContent.Auto, ImageMessageContent.DetailLevel.AUTO))

  // The SPI traits are sealed, so the spec cannot extend them. Each one compiles to an interface without methods.
  private def unknownValue[T <: AnyRef](implicit tag: ClassTag[T]): T = {
    val name = s"unknown ${tag.runtimeClass.getSimpleName}"
    val handler: InvocationHandler = (proxy: AnyRef, method: Method, args: Array[AnyRef]) =>
      method.getName match {
        case "equals"   => Boolean.box(proxy eq args(0))
        case "hashCode" => Int.box(System.identityHashCode(proxy))
        case "toString" => name
        case other      => throw new UnsupportedOperationException(other)
      }
    Proxy.newProxyInstance(tag.runtimeClass.getClassLoader, Array(tag.runtimeClass), handler).asInstanceOf[T]
  }

  "Mapping an SPI image detail level" should {

    "keep the mapping of every known detail level" in {
      detailLevels.map(_._2).toSet shouldBe ImageMessageContent.DetailLevel.values().toSet
      forAll(detailLevels) { (spiLevel, publicLevel) =>
        AgentImpl.fromSpiDetailLevel(spiLevel) shouldBe publicLevel
      }
    }

    "read a detail level that the SDK does not know as auto" in {
      val unknown = unknownValue[SpiAgent.ImageMessageContent.DetailLevel]
      forAll(detailLevels) { (known, _) => unknown should not be known }

      AgentImpl.fromSpiDetailLevel(unknown) shouldBe ImageMessageContent.DetailLevel.AUTO
    }

    "read a null detail level as auto" in {
      AgentImpl.fromSpiDetailLevel(null) shouldBe ImageMessageContent.DetailLevel.AUTO
    }
  }
}
