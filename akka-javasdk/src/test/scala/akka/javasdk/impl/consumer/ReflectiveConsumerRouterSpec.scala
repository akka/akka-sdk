/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.nio.charset.StandardCharsets
import java.util.Optional

import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.javasdk.Metadata
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AnySupport.BytesPrimitive
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.MethodInvoker
import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.BytesPayload
import akka.util.ByteString
import com.google.protobuf.GeneratedMessageV3
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import protoconsumer.EventsForConsumer

class ReflectiveConsumerRouterSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with LogCapturing {

  private val consumerProbe = createTestProbe[(Array[Byte], Metadata)]()

  object BytesOnlyConsumer {
    val methodInvokers = Map(
      BytesPrimitive.fullName -> MethodInvoker(classOf[BytesOnlyConsumer].getMethods.find(_.getName == "onBytes").get))
  }
  final class BytesOnlyConsumer extends Consumer {
    def onBytes(bytes: Array[Byte]): Consumer.Effect = {
      consumerProbe.ref ! (bytes, messageContext().metadata())
      effects().done()
    }
  }

  private def bytesOnlyRouter(ignoreUnknown: Boolean = false) = new ReflectiveConsumerRouter[BytesOnlyConsumer](
    new BytesOnlyConsumer,
    BytesOnlyConsumer.methodInvokers,
    new JsonSerializer,
    ignoreUnknown = ignoreUnknown,
    consumesFromTopic = false)

  private object ProtoConsumer {
    val methodInvokers = Map(
      "type.googleapis.com/consumer.EventForConsumer1" -> MethodInvoker(
        classOf[ProtoConsumer].getMethods.find(_.getName == "onEvent1").get),
      "type.googleapis.com/consumer.EventForConsumer2" -> MethodInvoker(
        classOf[ProtoConsumer].getMethods.find(_.getName == "onEvent2").get))

    val protoConsumerProbe = createTestProbe[GeneratedMessageV3]()
  }
  private final class ProtoConsumer extends Consumer {
    def onEvent1(event: EventsForConsumer.EventForConsumer1): Consumer.Effect = {
      ProtoConsumer.protoConsumerProbe.ref ! event
      effects().done()
    }

    def onEvent2(event: EventsForConsumer.EventForConsumer2): Consumer.Effect = {
      ProtoConsumer.protoConsumerProbe.ref ! event
      effects().done()
    }

  }

  private def protoBufRouter() = new ReflectiveConsumerRouter[ProtoConsumer](
    new ProtoConsumer,
    ProtoConsumer.methodInvokers,
    new JsonSerializer,
    ignoreUnknown = false,
    consumesFromTopic = false)

  "The ReflectiveConsumerRouter" should {

    "pass arbitrary events to raw handler" in {

      val metadata =
        MetadataImpl.Empty
          // Note: what we actually get from runtime right now "application/vnd.kalix.protobuf.any"
          //       this is then rewritten by ConsumerImpl into a valid media type
          .set("content-type", "application/json; type=someType")

      val messageContext = new MessageContextImpl(metadata, null, null, None, null, null)
      bytesOnlyRouter().handleCommand(
        // Note: payload comes with the type url in the field contentType, not an actual content type.
        MessageEnvelope.of(new BytesPayload(ByteString("some bytes"), "json.akka.io/someType")),
        messageContext)

      val (bytes, metadataFromConsumer) = consumerProbe.receiveMessage()
      new String(bytes, StandardCharsets.UTF_8) shouldEqual "some bytes"
      metadataFromConsumer.get("content-type") shouldEqual Optional.of("application/json; type=someType")
    }

    "not pass value entity delete events to raw handler" in {
      val metadata =
        MetadataImpl.Empty
          // Note: this is not rewritten by ConsumerImpl
          .set("content-type", "application/vnd.kalix.protobuf.any")

      val messageContext = new MessageContextImpl(metadata, null, null, None, null, null)
      bytesOnlyRouter(ignoreUnknown = true).handleCommand(
        // Note: payload comes with the type url in the field contentType, not an actual content type.
        MessageEnvelope.of(new BytesPayload(ByteString("some bytes"), BytesPayload.EmptyContentType)),
        messageContext)

      consumerProbe.expectNoMessage()
    }

    "special case handling of proto java messages for Kalix compatibility" in {
      val metadata1 =
        MetadataImpl.Empty
          // Note: what we actually get from runtime right now "application/vnd.kalix.protobuf.any"
          //       this is then rewritten by ConsumerImpl into a valid media type
          .set("content-type", "application/grpc+proto; message=consumer.EventForConsumer1")

      val event1 = EventsForConsumer.EventForConsumer1.newBuilder().setText("foo").build()
      val envelope1 =
        MessageEnvelope.of(
          new BytesPayload(ByteString(event1.toByteArray), "type.googleapis.com/consumer.EventForConsumer1"),
          metadata1)

      val metadata2 =
        MetadataImpl.Empty
          .set("content-type", "application/grpc+proto; message=consumer.EventForConsumer2")
      val event2 = EventsForConsumer.EventForConsumer2.newBuilder().setText("bar").build()
      val envelope2 =
        MessageEnvelope.of(
          new BytesPayload(ByteString(event2.toByteArray), "type.googleapis.com/consumer.EventForConsumer2"),
          metadata2)

      val router = protoBufRouter()

      val messageContext1 = new MessageContextImpl(metadata1, null, null, None, null, null)
      router.handleCommand(
        // Note: payload comes with the type url in the field contentType, not an actual content type.
        envelope1,
        messageContext1)
      ProtoConsumer.protoConsumerProbe.receiveMessage() shouldEqual event1

      val messageContext2 = new MessageContextImpl(metadata1, null, null, None, null, null)
      router.handleCommand(envelope2, messageContext2)
      ProtoConsumer.protoConsumerProbe.receiveMessage() shouldEqual event2

    }

  }
}
