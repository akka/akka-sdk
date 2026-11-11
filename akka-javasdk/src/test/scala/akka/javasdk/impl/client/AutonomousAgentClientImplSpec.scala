/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URI
import java.util.Optional

import akka.javasdk.agent.MessageContent
import akka.javasdk.agent.autonomous.AgentMessage
import akka.javasdk.agent.autonomous.MessageState
import akka.javasdk.agent.autonomous.MessageStatus
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAutonomousAgent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AutonomousAgentClientImplSpec extends AnyWordSpec with Matchers {

  // The SPI status trait is sealed, so the spec cannot extend it. It compiles to an interface without methods.
  private def unknownStatus: SpiAutonomousAgent.MessageStatus = {
    val statusClass = classOf[SpiAutonomousAgent.MessageStatus]
    val handler: InvocationHandler = (proxy: AnyRef, method: Method, args: Array[AnyRef]) =>
      method.getName match {
        case "equals"   => Boolean.box(proxy eq args(0))
        case "hashCode" => Int.box(System.identityHashCode(proxy))
        case "toString" => "unknown MessageStatus"
        case other      => throw new UnsupportedOperationException(other)
      }
    Proxy
      .newProxyInstance(statusClass.getClassLoader, Array[Class[_]](statusClass), handler)
      .asInstanceOf[SpiAutonomousAgent.MessageStatus]
  }

  "AutonomousAgentClientImpl.toMessageStatus" should {

    "convert each runtime status with its values" in {
      AutonomousAgentClientImpl.toMessageStatus(new SpiAutonomousAgent.MessageStatus.ForCurrentTask("t1")) shouldBe
      Some(new MessageStatus.ForCurrentTask("t1"))
      AutonomousAgentClientImpl.toMessageStatus(new SpiAutonomousAgent.MessageStatus.Held(Some("t2"))) shouldBe
      Some(new MessageStatus.Held(Optional.of("t2")))
      AutonomousAgentClientImpl.toMessageStatus(new SpiAutonomousAgent.MessageStatus.Held(None)) shouldBe
      Some(new MessageStatus.Held(Optional.empty()))
      AutonomousAgentClientImpl.toMessageStatus(new SpiAutonomousAgent.MessageStatus.Consumed("t3", "i3")) shouldBe
      Some(new MessageStatus.Consumed("t3", "i3"))
      AutonomousAgentClientImpl.toMessageStatus(new SpiAutonomousAgent.MessageStatus.Dropped("t4")) shouldBe
      Some(new MessageStatus.Dropped("t4"))
      AutonomousAgentClientImpl.toMessageStatus(new SpiAutonomousAgent.MessageStatus.Rejected("too many")) shouldBe
      Some(new MessageStatus.Rejected("too many"))
    }

    "read a status that the SDK does not know, and a null status, as None" in {
      AutonomousAgentClientImpl.toMessageStatus(unknownStatus) shouldBe None
      AutonomousAgentClientImpl.toMessageStatus(null) shouldBe None
    }
  }

  "AutonomousAgentClientImpl.toSpiMessage" should {

    "keep the text and the ids that are set" in {
      val spiMessage =
        AutonomousAgentClientImpl.toSpiMessage(AgentMessage.of("use metric units").messageId("m1").taskId("t1"))
      spiMessage.text shouldBe "use metric units"
      spiMessage.messageId shouldBe Some("m1")
      spiMessage.taskId shouldBe Some("t1")
      spiMessage.attachments shouldBe empty
    }

    "give None for the ids that are not set" in {
      val spiMessage = AutonomousAgentClientImpl.toSpiMessage(AgentMessage.of("use metric units"))
      spiMessage.messageId shouldBe None
      spiMessage.taskId shouldBe None
    }

    "convert an image attachment to a runtime image reference with the same URI" in {
      val uri = URI.create("https://example.com/chart.png")
      val spiMessage = AutonomousAgentClientImpl.toSpiMessage(
        AgentMessage
          .of("see the chart")
          .attach(new MessageContent.ImageUrlMessageContent(uri, MessageContent.ImageMessageContent.DetailLevel.AUTO)))
      spiMessage.attachments should have size 1
      spiMessage.attachments.head match {
        case image: SpiAgent.ImageUriMessageContent => image.uri shouldBe uri
        case other                                  => fail(s"Expected an image reference, got [$other]")
      }
    }
  }

  "AutonomousAgentClientImpl.toAcknowledgement" should {

    "keep the message id and duplicate" in {
      val acknowledgement = AutonomousAgentClientImpl.toAcknowledgement(
        new SpiAutonomousAgent.MessageAcknowledgement(
          "m1",
          new SpiAutonomousAgent.MessageStatus.Consumed("t1", "i1"),
          duplicate = true))
      acknowledgement.messageId shouldBe "m1"
      acknowledgement.status shouldBe new MessageStatus.Consumed("t1", "i1")
      acknowledgement.duplicate shouldBe true

      AutonomousAgentClientImpl
        .toAcknowledgement(
          new SpiAutonomousAgent.MessageAcknowledgement(
            "m2",
            new SpiAutonomousAgent.MessageStatus.Held(None),
            duplicate = false))
        .duplicate shouldBe false
    }

    "fail with the message id for a status that the SDK does not know" in {
      val exception = the[IllegalStateException] thrownBy AutonomousAgentClientImpl.toAcknowledgement(
        new SpiAutonomousAgent.MessageAcknowledgement("m1", unknownStatus, duplicate = false))
      exception.getMessage should include("[m1]")
      exception.getMessage should include("unknown MessageStatus")
    }
  }

  "AutonomousAgentClientImpl.toMessageStates" should {

    "keep the order and the statuses" in {
      val states = AutonomousAgentClientImpl.toMessageStates(
        Seq(
          new SpiAutonomousAgent.MessageState("m2", new SpiAutonomousAgent.MessageStatus.Dropped("t1")),
          new SpiAutonomousAgent.MessageState("m1", new SpiAutonomousAgent.MessageStatus.Consumed("t1", "i1")),
          new SpiAutonomousAgent.MessageState("m3", new SpiAutonomousAgent.MessageStatus.Held(Some("t2")))))
      states shouldBe java.util.List.of(
        new MessageState("m2", new MessageStatus.Dropped("t1")),
        new MessageState("m1", new MessageStatus.Consumed("t1", "i1")),
        new MessageState("m3", new MessageStatus.Held(Optional.of("t2"))))
    }

    "leave out a message with a status that the SDK does not know" in {
      val states = AutonomousAgentClientImpl.toMessageStates(
        Seq(
          new SpiAutonomousAgent.MessageState("m1", new SpiAutonomousAgent.MessageStatus.Held(None)),
          new SpiAutonomousAgent.MessageState("m2", unknownStatus),
          new SpiAutonomousAgent.MessageState("m3", null),
          new SpiAutonomousAgent.MessageState("m4", new SpiAutonomousAgent.MessageStatus.ForCurrentTask("t1"))))
      states shouldBe java.util.List.of(
        new MessageState("m1", new MessageStatus.Held(Optional.empty())),
        new MessageState("m4", new MessageStatus.ForCurrentTask("t1")))
    }
  }

  "AgentMessage" should {

    "reject an attachment with inline bytes" in {
      val inlineImage =
        new MessageContent.ImageBytesMessageContent(
          Array[Byte](1, 2, 3),
          Optional.of("image/png"),
          MessageContent.ImageMessageContent.DetailLevel.AUTO)
      an[IllegalArgumentException] should be thrownBy AgentMessage.of("see the image").attach(inlineImage)
    }

    "reject an empty message id and an empty task id" in {
      an[IllegalArgumentException] should be thrownBy AgentMessage.of("text").messageId("")
      an[IllegalArgumentException] should be thrownBy AgentMessage.of("text").taskId("")
    }

    "leave the instance unchanged when a value is set" in {
      val message = AgentMessage.of("text")
      message.messageId("m1")
      message.taskId("t1")
      message.attach(MessageContent.TextMessageContent.from("more"))
      message.text shouldBe "text"
      message.messageId shouldBe Optional.empty()
      message.taskId shouldBe Optional.empty()
      message.attachments shouldBe empty
    }
  }
}
