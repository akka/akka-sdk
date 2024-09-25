/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.lang
import java.net.URI
import java.nio.ByteBuffer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util
import java.util.Objects
import java.util.Optional

import scala.compat.java8.OptionConverters._
import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.http.javadsl.model.StatusCode
import akka.javasdk.CloudEvent
import akka.javasdk.JwtClaims
import akka.javasdk.Metadata
import akka.javasdk.Principals
import akka.javasdk.TraceContext
import akka.javasdk.impl.MetadataImpl.JwtClaimPrefix
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.impl.telemetry.Telemetry.metadataGetter
import com.google.protobuf.ByteString
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.{ Context => OtelContext }
import kalix.protocol.component
import kalix.protocol.component.MetadataEntry

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] class MetadataImpl private (val entries: Seq[MetadataEntry]) extends Metadata with CloudEvent {

  override def has(key: String): Boolean = entries.exists(_.key.equalsIgnoreCase(key))

  override def get(key: String): Optional[String] =
    getScala(key).asJava

  private[akka] def getScala(key: String): Option[String] =
    entries.collectFirst {
      case MetadataEntry(k, MetadataEntry.Value.StringValue(value), _) if key.equalsIgnoreCase(k) =>
        value
    }

  def withTracing(spanContext: SpanContext): Metadata = {
    withTracing(Span.wrap(spanContext))
  }

  def withTracing(span: Span): MetadataImpl = {
    // remove parent trace parent and trace state from the metadata so they can be re-injected with current span context
    val builder = Vector.newBuilder[MetadataEntry]
    builder.addAll(
      entries.iterator.filter(m => m.key != Telemetry.TRACE_PARENT_KEY && m.key != Telemetry.TRACE_STATE_KEY))
    W3CTraceContextPropagator
      .getInstance()
      .inject(io.opentelemetry.context.Context.current().`with`(span), builder, Telemetry.builderSetter)
    MetadataImpl.of(builder.result())
  }

  override def getAll(key: String): util.List[String] =
    getAllScala(key).asJava

  private[akka] def getAllScala(key: String): Seq[String] =
    entries.collect {
      case MetadataEntry(k, MetadataEntry.Value.StringValue(value), _) if key.equalsIgnoreCase(k) =>
        value
    }

  override def getBinary(key: String): Optional[ByteBuffer] =
    getBinaryScala(key).asJava

  private[akka] def getBinaryScala(key: String): Option[ByteBuffer] =
    entries.collectFirst {
      case MetadataEntry(k, MetadataEntry.Value.BytesValue(value), _) if key.equalsIgnoreCase(k) =>
        value.asReadOnlyByteBuffer()
    }

  override def getBinaryAll(key: String): util.List[ByteBuffer] =
    getBinaryAllScala(key).asJava

  private[akka] def getBinaryAllScala(key: String): Seq[ByteBuffer] =
    entries.collect {
      case MetadataEntry(k, MetadataEntry.Value.BytesValue(value), _) if key.equalsIgnoreCase(k) =>
        value.asReadOnlyByteBuffer()
    }

  override def getAllKeys: util.List[String] = getAllKeysScala.asJava
  private[akka] def getAllKeysScala: Seq[String] = entries.map(_.key)

  override def set(key: String, value: String): MetadataImpl = {
    Objects.requireNonNull(key, "Key must not be null")
    Objects.requireNonNull(value, "Value must not be null")
    MetadataImpl.of(removeKey(key) :+ MetadataEntry(key, MetadataEntry.Value.StringValue(value)))
  }

  override def setBinary(key: String, value: ByteBuffer): MetadataImpl = {
    Objects.requireNonNull(key, "Key must not be null")
    Objects.requireNonNull(value, "Value must not be null")
    MetadataImpl.of(removeKey(key) :+ MetadataEntry(key, MetadataEntry.Value.BytesValue(ByteString.copyFrom(value))))
  }

  override def add(key: String, value: String): MetadataImpl = {
    Objects.requireNonNull(key, "Key must not be null")
    Objects.requireNonNull(value, "Value must not be null")
    MetadataImpl.of(entries :+ MetadataEntry(key, MetadataEntry.Value.StringValue(value)))
  }

  override def addBinary(key: String, value: ByteBuffer): MetadataImpl = {
    Objects.requireNonNull(key, "Key must not be null")
    Objects.requireNonNull(value, "Value must not be null")
    MetadataImpl.of(entries :+ MetadataEntry(key, MetadataEntry.Value.BytesValue(ByteString.copyFrom(value))))
  }

  override def remove(key: String): MetadataImpl = MetadataImpl.of(removeKey(key))

  override def clear(): MetadataImpl = MetadataImpl.Empty

  private[akka] def iteratorScala[R](f: MetadataEntry => R): Iterator[R] =
    entries.iterator.map(f)

  override def iterator(): util.Iterator[Metadata.MetadataEntry] =
    iteratorScala(entry =>
      new Metadata.MetadataEntry {
        override def getKey: String = entry.key
        override def getValue: String = entry.value.stringValue.orNull
        override def getBinaryValue: ByteBuffer = entry.value.bytesValue.map(_.asReadOnlyByteBuffer()).orNull
        override def isText: Boolean = entry.value.isStringValue
        override def isBinary: Boolean = entry.value.isBytesValue
      }).asJava

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
        MetadataEntry(MetadataImpl.CeSpecversion, MetadataEntry.Value.StringValue(MetadataImpl.CeSpecversionValue)),
        MetadataEntry(MetadataImpl.CeId, MetadataEntry.Value.StringValue(id)),
        MetadataEntry(MetadataImpl.CeSource, MetadataEntry.Value.StringValue(source.toString)),
        MetadataEntry(MetadataImpl.CeType, MetadataEntry.Value.StringValue(`type`))))

  private def getRequiredCloudEventField(key: String) =
    entries
      .collectFirst {
        case MetadataEntry(k, MetadataEntry.Value.StringValue(value), _) if key.equalsIgnoreCase(k) =>
          value
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

  override def datacontenttype(): Optional[String] = getScala(MetadataImpl.CeDatacontenttype).asJava
  private[akka] def datacontenttypeScala(): Option[String] = getScala(MetadataImpl.CeDatacontenttype)

  override def withDatacontenttype(datacontenttype: String): MetadataImpl =
    set(MetadataImpl.CeDatacontenttype, datacontenttype)

  override def clearDatacontenttype(): MetadataImpl = remove(MetadataImpl.CeDatacontenttype)

  override def dataschema(): Optional[URI] = dataschemaScala().asJava
  private[akka] def dataschemaScala(): Option[URI] = getScala(MetadataImpl.CeDataschema).map(URI.create(_))

  override def withDataschema(dataschema: URI): MetadataImpl = set(MetadataImpl.CeDataschema, dataschema.toString)

  override def clearDataschema(): MetadataImpl = remove(MetadataImpl.CeDataschema)

  override def subject(): Optional[String] = subjectScala.asJava
  private[akka] def subjectScala: Option[String] = getScala(MetadataImpl.CeSubject)

  override def withSubject(subject: String): MetadataImpl = set(MetadataImpl.CeSubject, subject)

  override def clearSubject(): MetadataImpl = remove(MetadataImpl.CeSubject)

  override def time(): Optional[ZonedDateTime] = timeScala.asJava
  private[akka] def timeScala: Option[ZonedDateTime] =
    getScala(MetadataImpl.CeTime).map(ZonedDateTime.parse(_))

  override def withTime(time: ZonedDateTime): MetadataImpl =
    set(MetadataImpl.CeTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time))

  override def clearTime(): MetadataImpl = remove(MetadataImpl.CeTime)

  override def withStatusCode(code: StatusCode): MetadataImpl =
    set("_kalix-http-code", code.intValue().toString)

  override def asMetadata(): Metadata = this

  // The reason we don't just implement JwtClaims ourselves is that some of the methods clash with CloudEvent
  override lazy val jwtClaims: JwtClaims = new JwtClaims {
    override def allClaimNames(): lang.Iterable[String] = allJwtClaimNames.asJava
    override def asMap(): util.Map[String, String] = jwtClaimsAsMap.asJava
    override def getString(name: String): Optional[String] = getJwtClaim(name).asJava
  }

  override lazy val principals: Principals =
    PrincipalsImpl(getScala(MetadataImpl.PrincipalsSource), getScala(MetadataImpl.PrincipalsService))

  override lazy val traceContext: TraceContext = new TraceContext {
    override def asOpenTelemetryContext(): OtelContext = W3CTraceContextPropagator
      .getInstance()
      .extract(OtelContext.current(), asMetadata(), metadataGetter)

    override def traceId(): Optional[String] = {
      Span.fromContext(asOpenTelemetryContext()).getSpanContext.getTraceId match {
        case "00000000000000000000000000000000" =>
          Optional.empty() // when no traceId returns io.opentelemetry.api.trace.TraceId.INVALID
        case traceId => Some(traceId).asJava
      }
    }

    override def traceParent(): Optional[String] = getScala(Telemetry.TRACE_PARENT_KEY).asJava

    override def traceState(): Optional[String] = getScala(Telemetry.TRACE_STATE_KEY).asJava
  }

  private[akka] def allJwtClaimNames: Iterable[String] =
    entries.view.collect {
      case MetadataEntry(key, MetadataEntry.Value.StringValue(_), _) if key.startsWith(JwtClaimPrefix) => key
    }

  private[akka] def jwtClaimsAsMap: Map[String, String] =
    entries.view.collect {
      case MetadataEntry(key, MetadataEntry.Value.StringValue(value), _) if key.startsWith(JwtClaimPrefix) =>
        key -> value
    }.toMap

  private[akka] def getJwtClaim(name: String): Option[String] = {
    val prefixedName = JwtClaimPrefix + name
    entries.collectFirst {
      case MetadataEntry(key, MetadataEntry.Value.StringValue(value), _) if key == prefixedName => value
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
  val CeRequired: Set[String] = Set(CeSpecversion, CeId, CeSource, CeType)
  private val AllCeAttributes = CeRequired ++ Set(CeDataschema, CeDatacontenttype, CeSubject, CeTime)

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

  val JwtClaimPrefix = "_kalix-jwt-claim-"

  val PrincipalsSource = "_kalix-src"
  val PrincipalsService = "_kalix-src-svc"

  def toProtocol(metadata: Metadata): Option[component.Metadata] =
    metadata match {
      case impl: MetadataImpl if impl.entries.nonEmpty =>
        Some(component.Metadata(impl.entries))
      case _: MetadataImpl => None
      case other =>
        throw new RuntimeException(s"Unknown metadata implementation: ${other.getClass}, cannot send")
    }

  def of(entries: Seq[MetadataEntry]): MetadataImpl = {
    val transformedEntries =
      entries.map { entry =>
        // is incoming ce key in one of the alternative formats?
        // if so, convert key to our internal default key format
        alternativeKeyFormats.get(entry.key) match {
          case Some(defaultKey) => MetadataEntry(defaultKey, entry.value)
          case _                => entry
        }
      }

    new MetadataImpl(transformedEntries)
  }

}
