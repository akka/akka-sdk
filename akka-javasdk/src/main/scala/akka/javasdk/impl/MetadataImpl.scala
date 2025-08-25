/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.net.URI
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util
import java.util.Objects
import java.util.Optional

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.CloudEvent
import akka.javasdk.Metadata
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.Telemetry.metadataGetter
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiMetadataEntry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] class MetadataImpl private (val entries: Seq[SpiMetadataEntry]) extends Metadata with CloudEvent {

  override def has(key: String): Boolean = entries.exists(_.key.equalsIgnoreCase(key))

  override def get(key: String): Optional[String] =
    getScala(key).toJava

  private[akka] def getScala(key: String): Option[String] =
    entries.collectFirst {
      case entry if key.equalsIgnoreCase(entry.key) => entry.value
    }

  // FIXME(tracing): have context propagators provided by the runtime
  def withTelemetryContext(context: OtelContext): MetadataImpl = {
    val builder = Vector.newBuilder[SpiMetadataEntry]
    builder.addAll(entries)
    W3CTraceContextPropagator.getInstance.inject(context, builder, Telemetry.builderSetter)
    MetadataImpl.of(builder.result())
  }

  override def getAll(key: String): util.List[String] =
    getAllScala(key).asJava

  private[akka] def getAllScala(key: String): Seq[String] =
    entries.collect {
      case entry if key.equalsIgnoreCase(entry.key) => entry.value
    }

  override def getBinary(key: String): Optional[ByteBuffer] =
    Optional.empty[ByteBuffer] // binary not supported

  override def getBinaryAll(key: String): util.List[ByteBuffer] =
    util.Collections.emptyList()

  override def getAllKeys: util.List[String] = getAllKeysScala.asJava
  private[akka] def getAllKeysScala: Seq[String] = entries.map(_.key)

  override def set(key: String, value: String): MetadataImpl = {
    Objects.requireNonNull(key, "Key must not be null")
    Objects.requireNonNull(value, "Value must not be null")
    MetadataImpl.of(removeKey(key) :+ new SpiMetadataEntry(key, value))
  }

  override def setBinary(key: String, value: ByteBuffer): MetadataImpl = {
    Objects.requireNonNull(key, "Key must not be null")
    Objects.requireNonNull(value, "Value must not be null")
    // binary not supported
    this
  }

  override def add(key: String, value: String): MetadataImpl = {
    Objects.requireNonNull(key, "Key must not be null")
    Objects.requireNonNull(value, "Value must not be null")
    MetadataImpl.of(entries :+ new SpiMetadataEntry(key, value))
  }

  override def addBinary(key: String, value: ByteBuffer): MetadataImpl = {
    Objects.requireNonNull(key, "Key must not be null")
    Objects.requireNonNull(value, "Value must not be null")
    // binary not supported
    this
  }

  override def remove(key: String): MetadataImpl = MetadataImpl.of(removeKey(key))

  override def clear(): MetadataImpl = MetadataImpl.Empty

  override def iterator(): util.Iterator[Metadata.MetadataEntry] = {
    entries.iterator.map { entry =>
      new Metadata.MetadataEntry {
        override def getKey: String = entry.key
        override def getValue: String = entry.value
        override def getBinaryValue: ByteBuffer = null
        override def isText: Boolean = true
        override def isBinary: Boolean = false
      }
    }.asJava
  }

  private def removeKey(key: String) = entries.filterNot(_.key.equalsIgnoreCase(key))

  def isCloudEvent: Boolean = MetadataImpl.CeRequired.forall(h => has(h))

  override def asCloudEvent(): MetadataImpl =
    if (!isCloudEvent) {
      throw new IllegalStateException("Metadata is not a CloudEvent!")
    } else this

  override def asCloudEvent(id: String, source: URI, `type`: String): MetadataImpl =
    MetadataImpl.of(
      entries.filterNot(e => MetadataImpl.CeRequired(e.key)) ++
      Seq(
        new SpiMetadataEntry(MetadataImpl.CeSpecversion, MetadataImpl.CeSpecversionValue),
        new SpiMetadataEntry(MetadataImpl.CeId, id),
        new SpiMetadataEntry(MetadataImpl.CeSource, source.toString),
        new SpiMetadataEntry(MetadataImpl.CeType, `type`)))

  private def getRequiredCloudEventField(key: String) =
    entries
      .collectFirst {
        case entry if key.equalsIgnoreCase(entry.key) => entry.value
      }
      .getOrElse {
        throw new IllegalStateException(s"Metadata is not a CloudEvent because it does not have required field $key")
      }

  override def specversion(): String = getRequiredCloudEventField(MetadataImpl.CeSpecversion)

  override def id(): String = getRequiredCloudEventField(MetadataImpl.CeId)

  override def withId(id: String): MetadataImpl = set(MetadataImpl.CeId, id)

  override def source(): URI = URI.create(getRequiredCloudEventField(MetadataImpl.CeSource))

  override def withSource(source: URI): MetadataImpl = set(MetadataImpl.CeSource, source.toString)

  override def `type`(): String = getRequiredCloudEventField(MetadataImpl.CeType)

  override def withType(`type`: String): MetadataImpl = set(MetadataImpl.CeType, `type`)

  override def datacontenttype(): Optional[String] = getScala(MetadataImpl.CeDatacontenttype).toJava
  private[akka] def datacontenttypeScala(): Option[String] = getScala(MetadataImpl.CeDatacontenttype)

  override def withDatacontenttype(datacontenttype: String): MetadataImpl =
    set(MetadataImpl.CeDatacontenttype, datacontenttype)

  override def clearDatacontenttype(): MetadataImpl = remove(MetadataImpl.CeDatacontenttype)

  override def dataschema(): Optional[URI] = dataschemaScala().toJava
  private[akka] def dataschemaScala(): Option[URI] = getScala(MetadataImpl.CeDataschema).map(URI.create(_))

  override def withDataschema(dataschema: URI): MetadataImpl = set(MetadataImpl.CeDataschema, dataschema.toString)

  override def clearDataschema(): MetadataImpl = remove(MetadataImpl.CeDataschema)

  override def subject(): Optional[String] = subjectScala.toJava
  private[akka] def subjectScala: Option[String] = getScala(MetadataImpl.CeSubject)

  override def withSubject(subject: String): MetadataImpl = set(MetadataImpl.CeSubject, subject)

  override def clearSubject(): MetadataImpl = remove(MetadataImpl.CeSubject)

  override def sequence(): Optional[java.lang.Long] =
    getScala(MetadataImpl.CeSequence).flatMap(_.toLongOption).map(Long.box).toJava
  override def sequenceString(): Optional[String] = getScala(MetadataImpl.CeSequence).toJava
  override def withSequence(sequence: String): MetadataImpl = set(MetadataImpl.CeSequence, sequence)
  override def clearSequence(): MetadataImpl = remove(MetadataImpl.CeSequence)

  override def sequenceType(): Optional[String] = getScala(MetadataImpl.CeSequenceType).toJava
  override def withSequenceType(sequenceType: String): MetadataImpl = set(MetadataImpl.CeSequenceType, sequenceType)
  override def clearSequenceType(): MetadataImpl = remove(MetadataImpl.CeSequenceType)

  override def time(): Optional[ZonedDateTime] = timeScala.toJava
  private[akka] def timeScala: Option[ZonedDateTime] =
    getScala(MetadataImpl.CeTime).map(ZonedDateTime.parse(_))

  override def withTime(time: ZonedDateTime): MetadataImpl =
    set(MetadataImpl.CeTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time))

  override def clearTime(): MetadataImpl = remove(MetadataImpl.CeTime)

  override def asMetadata(): Metadata = this

  lazy val traceId: Option[String] = {
    val otelContext = W3CTraceContextPropagator
      .getInstance()
      .extract(OtelContext.current(), asMetadata(), metadataGetter)

    Span.fromContext(otelContext).getSpanContext.getTraceId match {
      case "00000000000000000000000000000000" =>
        None // when no traceId returns io.opentelemetry.api.trace.TraceId.INVALID
      case traceId => Some(traceId)
    }
  }

  override def merge(other: Metadata): Metadata = {
    val otherImpl = other.asInstanceOf[MetadataImpl]
    MetadataImpl.of(entries ++ otherImpl.entries)
  }
}

object MetadataImpl {
  val CeSpecversion = "ce-specversion"
  val CeSpecversionValue = "1.0"
  val CeId = "ce-id"
  val CeSource = "ce-source"
  val CeType = "ce-type"
  // As per CloudEvent HTTP encoding spec, we use Content-Type to encode this.
  val CeDatacontenttype = "Content-Type"
  val CeDataschema = "ce-dataschema"
  val CeSubject = "ce-subject"
  val CeTime = "ce-time"
  val CeSequence = "ce-sequence"
  val CeSequenceType = "ce-sequencetype"
  val CeRequired: Set[String] = Set(CeSpecversion, CeId, CeSource, CeType)
  private val AllCeAttributes =
    CeRequired ++ Set(CeDataschema, CeDatacontenttype, CeSubject, CeTime, CeSequence, CeSequenceType)

  /**
   * Maps alternative prefixed keys to our default key format, ie: ce-.
   *
   * For the moment, only the Kafka prefix is in use, ie: ce_, but others might be needed in future.
   */
  private val alternativeKeyFormats = AllCeAttributes.map { attr =>
    val key = attr.replaceFirst("^ce-", "ce_")
    (key, attr)
  }.toMap

  val Empty = MetadataImpl.of(Vector.empty)

  def toSpi(metadata: Option[Metadata]): SpiMetadata =
    metadata.map(toSpi).getOrElse(SpiMetadata.empty)

  def toSpi(metadata: Metadata): SpiMetadata = {
    metadata match {
      case impl: MetadataImpl if impl.entries.nonEmpty =>
        new SpiMetadata(impl.entries)
      case _: MetadataImpl =>
        SpiMetadata.empty
      case other =>
        throw new RuntimeException(s"Unknown metadata implementation: ${other.getClass}, cannot send")
    }
  }

  def of(entries: Seq[SpiMetadataEntry]): MetadataImpl = {
    val transformedEntries =
      entries.map { entry =>
        // is incoming ce key in one of the alternative formats?
        // if so, convert key to our internal default key format
        alternativeKeyFormats.get(entry.key) match {
          case Some(defaultKey) => new SpiMetadataEntry(defaultKey, entry.value)
          case _                => entry
        }
      }

    ensureContentType(new MetadataImpl(transformedEntries))
  }

  def of(metadata: SpiMetadata): MetadataImpl = {
    of(metadata.entries)
  }

  private def ensureContentType(metadata: MetadataImpl): MetadataImpl = {
    // NOTE: MetadataImpl currently always uses `Content-Type` for `ce-datacontenttype`.
    // If the metadata only has `ce-datacontenttype`, also copy it to `Content-Type`.
    if (metadata.datacontenttype.isEmpty) {
      metadata.getScala("ce-datacontenttype") match {
        case Some(contentType) => metadata.withDatacontenttype(contentType)
        case _                 => metadata
      }
    } else metadata
  }

}
