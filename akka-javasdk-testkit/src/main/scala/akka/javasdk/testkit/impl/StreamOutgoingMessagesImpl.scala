/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable
import scala.util.Failure
import scala.util.Success

import akka.Done
import akka.NotUsed
import akka.actor.CoordinatedShutdown
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.MetadataBuilder
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.serialization.Serializer
import akka.persistence.Persistence
import akka.persistence.query.Offset
import akka.persistence.query.typed.EventEnvelope
import akka.projection.AllowSeqNrGapsMetadata
import akka.projection.grpc.consumer.GrpcQuerySettings
import akka.projection.grpc.consumer.scaladsl.GrpcReadJournal
import akka.stream.KillSwitches
import akka.stream.Materializer
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
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
 * Captures events emitted by a `@Produce.ServiceStream` producer by consuming the running service's producer stream via
 * Akka Projection gRPC. No runtime mocking is involved — the real transformation path runs.
 *
 * INTERNAL API
 */
@InternalApi
private[testkit] object StreamOutgoingMessagesImpl {

  private val log = LoggerFactory.getLogger("akka.javasdk.testkit.impl.StreamOutgoingMessagesImpl")

  private val GoogleTypeUrlPrefix = AnySupport.DefaultTypeUrlPrefix + "/"
  private val ProtoAnyTypeUrl = GoogleTypeUrlPrefix + com.google.protobuf.Any.getDescriptor.getFullName

  // Envelope sources set by the event producer.
  private val SourceBacktracking = "BT"
  private val SourceSnapshot = "SN"

  def apply(
      system: ActorSystem[_],
      runtimeHost: String,
      runtimePort: Int,
      serviceIdentityHeader: Option[String],
      serviceIdentityToken: Option[String],
      service: String,
      streamId: String,
      serializer: Serializer): OutgoingMessagesImpl = {

    val classic = system.classicSystem
    val materializer: Materializer = SystemMaterializer(system).materializer
    implicit val ec = system.executionContext
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

    // all slices — testkit runs a single instance covering the full range
    val sliceRange = Persistence(classic).sliceRanges(1).head

    val shuttingDown = new AtomicBoolean(false)

    val (killSwitch, done) = journal
      .eventsBySlices[AnyRef](streamId, sliceRange.min, sliceRange.max, Offset.noOffset)
      .viaMat(KillSwitches.single)(Keep.right)
      .via(offsetStoreFilter(streamId))
      .toMat(Sink.foreach { (env: EventEnvelope[AnyRef]) =>
        toEmitSingleCommand(env, streamId).foreach(cmd => probe.ref ! cmd)
      })(Keep.both)
      .run()(materializer)

    done.onComplete {
      case Success(_) =>
        log.debug("Stream [{}] outgoing subscription completed", streamId)
      case Failure(_) if shuttingDown.get() =>
        log.debug("Stream [{}] outgoing subscription terminated during shutdown", streamId)
      case Failure(ex) =>
        log.warn(
          "Stream [{}] outgoing subscription failed — expectations on this stream will time out: {}",
          streamId,
          ex.toString,
          ex)
    }

    CoordinatedShutdown(classic).addTask(
      CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
      s"stream-outgoing-testkit-shutdown-$service-$streamId") { () =>
      shuttingDown.set(true)
      killSwitch.shutdown()
      // wait for the stream to fully drain so the actor system doesn't proceed to terminate
      // mid-teardown, which is what produces abrupt shutdown noise.
      done.recover { case _ => Done }
    }

    new OutgoingMessagesImpl(probe, serializer)
  }

  // The producer merges its database query with pub-sub and does not remove copies. Once it accepts pub-sub events,
  // each event arrives twice, first from pub-sub and then from the query. A pub-sub event can also arrive before an
  // earlier event that waits for the next query poll. Backtracking envelopes arrive later, without a payload.
  //
  // This flow applies the rules of the offset store of a consuming projection. It keeps the last accepted sequence
  // number per persistence id. A number at or below it is a copy. A strict envelope must be the next number, and a gap
  // is dropped. A pub-sub event ahead of a gap arrives again from the query, after the missing events. A snapshot, or
  // an envelope with AllowSeqNrGapsMetadata, may be any higher number. An envelope without a payload does not move the
  // number, because the copy with the payload can arrive after it. A filtered envelope moves the number, so the next
  // event is not a gap. The state lives in the stage, so `clear()` on the handle does not reset it.
  private[impl] def offsetStoreFilter(streamId: String): Flow[EventEnvelope[AnyRef], EventEnvelope[AnyRef], NotUsed] =
    Flow[EventEnvelope[AnyRef]].statefulMapConcat { () =>
      val acceptedSeqNrs = mutable.Map.empty[String, Long]

      env => {
        val persistenceId = env.persistenceId
        val seqNr = env.sequenceNr
        val accepted = acceptedSeqNrs.getOrElse(persistenceId, 0L)

        if (!env.filtered && env.eventOption.isEmpty) {
          // Backtracking runs well behind the query. When it finds the next number, the query missed that event, and
          // the event does not arrive with a payload.
          if (env.source == SourceBacktracking && seqNr == accepted + 1)
            log.warn(
              "Stream [{}] did not receive persistence id [{}] sequence number [{}] with a payload. The testkit does " +
              "not deliver it, or later events of that persistence id that must follow it in sequence.",
              streamId,
              persistenceId,
              seqNr)
          Nil
        } else if (seqNr <= accepted) {
          Nil
        } else if (seqNr == accepted + 1 || !strictSeqNr(env)) {
          acceptedSeqNrs.update(persistenceId, seqNr)
          env :: Nil
        } else {
          log.debug(
            "Stream [{}] dropped persistence id [{}] sequence number [{}] from source [{}], expected [{}]",
            streamId,
            persistenceId,
            seqNr,
            env.source,
            accepted + 1)
          Nil
        }
      }
    }

  private def strictSeqNr(env: EventEnvelope[AnyRef]): Boolean =
    env.source != SourceSnapshot && env.metadata[AllowSeqNrGapsMetadata.type].isEmpty

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
            if (tu.startsWith(AnySupport.JsonTypeUrlPrefix))
              (tu.substring(AnySupport.JsonTypeUrlPrefix.length), "application/json")
            else if (isProto) (tu.substring(GoogleTypeUrlPrefix.length), "application/protobuf")
            else (tu, "application/octet-stream")

          // Non-protobuf payloads (e.g. JSON) are length-delim wrapped by the runtime as a primitive-bytes
          // field when packed into ScalaPbAny; unwrap them via the SDK's primitive-bytes decoder.
          val payloadBytes: ByteString =
            if (isProto || any.value.isEmpty) any.value
            else AnySupport.decodePrimitiveBytes(any.value)

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
