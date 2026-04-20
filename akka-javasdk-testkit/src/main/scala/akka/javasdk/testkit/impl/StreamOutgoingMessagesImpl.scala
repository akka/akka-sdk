/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.actor.typed.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.MetadataBuilder
import akka.javasdk.impl.serialization.Serializer
import akka.persistence.query.Offset
import akka.persistence.query.typed.EventEnvelope
import akka.projection.grpc.consumer.GrpcQuerySettings
import akka.projection.grpc.consumer.scaladsl.GrpcReadJournal
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import com.google.protobuf.ByteString
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.protocol.component.Metadata
import kalix.protocol.component.MetadataEntry
import kalix.testkit.protocol.eventing_test_backend.EmitSingleCommand
import kalix.testkit.protocol.eventing_test_backend.{ Message => TestkitMessage }
import org.slf4j.LoggerFactory

/**
 * Captures events produced by a `@Produce.ServiceStream` publisher by subscribing to the running service's producer
 * stream via Akka Projection gRPC. No runtime mocking is involved — the real transformation path runs.
 */
private[testkit] object StreamOutgoingMessagesImpl {

  private val log = LoggerFactory.getLogger(classOf[OutgoingMessagesImpl])

  private val GoogleTypeUrlPrefix = "type.googleapis.com/"
  private val JsonTypeUrlPrefix = "json.akka.io/"
  private val ProtoAnyTypeUrl = "type.googleapis.com/google.protobuf.Any"

  def apply(
      system: ActorSystem[_],
      runtimeHost: String,
      runtimePort: Int,
      serviceIdentityHeader: Option[String],
      serviceIdentityToken: Option[String],
      streamId: String,
      serializer: Serializer): OutgoingMessagesImpl = {

    val classic = system.classicSystem
    val materializer: Materializer = SystemMaterializer(system).materializer
    val probe = TestProbe()(classic)

    val clientSettings = GrpcClientSettings
      .connectToServiceAt(runtimeHost, runtimePort)(classic)
      .withTls(false)

    val querySettings = {
      val base = GrpcQuerySettings(streamId)
      (serviceIdentityHeader, serviceIdentityToken) match {
        case (Some(header), Some(token)) =>
          base.withAdditionalRequestMetadata(new MetadataBuilder().addText(header, token).build())
        case _ => base
      }
    }

    val journal = GrpcReadJournal(querySettings, clientSettings, Seq.empty)(classic)

    val done = journal
      .eventsBySlices[AnyRef](streamId, 0, 1023, Offset.noOffset)
      .runWith(Sink.foreach { (env: EventEnvelope[AnyRef]) =>
        toEmitSingleCommand(env, streamId).foreach(cmd => probe.ref ! cmd)
      })(materializer)

    done.failed.foreach(ex => log.debug("Stream outgoing subscription terminated: {}", ex.toString))(
      system.executionContext)

    new OutgoingMessagesImpl(probe, serializer)
  }

  private def toEmitSingleCommand(env: EventEnvelope[AnyRef], streamId: String): Option[EmitSingleCommand] = {
    if (env.filtered || env.eventOption.isEmpty) None
    else {
      env.event match {
        case outer: ScalaPbAny =>
          // The runtime wraps produced events as ScalaPbAny(typeUrl=google.protobuf.Any, value=innerScalaPbAny)
          // for passthrough — unwrap one level when we see that.
          val any =
            if (outer.typeUrl == ProtoAnyTypeUrl) ScalaPbAny.parseFrom(outer.value.newCodedInput())
            else outer
          log.debug("Stream [{}] received event typeUrl=[{}]", streamId, any.typeUrl)
          val tu = any.typeUrl
          val isProto = tu.startsWith(GoogleTypeUrlPrefix)
          val (ceType, contentType) =
            if (tu.startsWith(JsonTypeUrlPrefix)) (tu.substring(JsonTypeUrlPrefix.length), "application/json")
            else if (isProto) (tu.substring(GoogleTypeUrlPrefix.length), "application/protobuf")
            else (tu, "application/octet-stream")

          // Non-protobuf payloads are length-delim wrapped as field 1 by the runtime's ProtobufUtils;
          // unwrap to get the raw (JSON) bytes.
          val payloadBytes: ByteString =
            if (isProto || any.value.isEmpty) any.value
            else {
              val in = any.value.newCodedInput()
              in.readTag()
              in.readBytes()
            }

          val metadata = Metadata(entries = Seq(
            MetadataEntry("ce-type", MetadataEntry.Value.StringValue(ceType)),
            MetadataEntry("Content-Type", MetadataEntry.Value.StringValue(contentType))))

          Some(
            EmitSingleCommand(
              destination = None,
              message = Some(TestkitMessage(payload = payloadBytes, metadata = Some(metadata)))))

        case other =>
          log.warn(
            "Stream [{}] produced an event of unexpected type [{}]; only ScalaPbAny pass-through is supported in the testkit.",
            streamId,
            other.getClass.getName)
          None
      }
    }
  }
}
