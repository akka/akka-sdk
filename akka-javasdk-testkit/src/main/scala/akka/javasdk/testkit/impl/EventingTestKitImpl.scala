/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.impl

import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.javasdk.JsonSupport
import akka.javasdk.Metadata.{ MetadataEntry => SdkMetadataEntry }
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.testkit.EventingTestKit
import akka.javasdk.testkit.EventingTestKit.IncomingMessages
import akka.javasdk.testkit.EventingTestKit.OutgoingMessages
import akka.javasdk.testkit.EventingTestKit.{ Message => TestKitMessage }
import akka.javasdk.testkit.impl.EventingTestKitImpl.RunningSourceProbe
import akka.javasdk.testkit.impl.TestKitMessageImpl.defaultMetadata
import akka.javasdk.testkit.impl.TestKitMessageImpl.expectMsgInternal
import akka.javasdk.{ Metadata => SdkMetadata }
import akka.pattern._
import akka.stream.BoundedSourceQueue
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.testkit.TestProbe
import akka.util.BoxedType
import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.eventing.DirectSource
import kalix.eventing.EventSource
import kalix.protocol.component.Metadata
import kalix.protocol.component.MetadataEntry
import kalix.testkit.protocol.eventing_test_backend.EmitSingleCommand
import kalix.testkit.protocol.eventing_test_backend.EmitSingleResult
import kalix.testkit.protocol.eventing_test_backend.EventStreamOutCommand
import kalix.testkit.protocol.eventing_test_backend.EventStreamOutResult
import kalix.testkit.protocol.eventing_test_backend.EventingTestKitService
import kalix.testkit.protocol.eventing_test_backend.EventingTestKitServiceHandler
import kalix.testkit.protocol.eventing_test_backend.Message
import kalix.testkit.protocol.eventing_test_backend.RunSourceCommand
import kalix.testkit.protocol.eventing_test_backend.RunSourceCreate
import kalix.testkit.protocol.eventing_test_backend.SourceElem
import org.slf4j.LoggerFactory
import scalapb.GeneratedMessage
import java.time
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.{ List => JList }

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.jdk.OptionConverters._
import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.javasdk.impl.serialization.JsonSerializer
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiMetadataEntry

object EventingTestKitImpl {

  /**
   * Start a pretend eventing backend, configure the runtime to use this through the
   * 'akka.javasdk.dev-mode.eventing.support' "grpc-backend" and the same host and port as this was started with.
   *
   * The returned testkit can be used to expect and emit events to the proxy as if they came from an actual pub/sub
   * event backend.
   */
  def start(system: ActorSystem[_], host: String, port: Int, serializer: JsonSerializer): EventingTestKit = {

    // Create service handlers
    val service = new EventingTestServiceImpl(system, host, port, serializer)
    val handler: HttpRequest => Future[HttpResponse] =
      EventingTestKitServiceHandler(new service.ServiceImpl)(system)

    val binding = Await.result(
      Http(system)
        .newServerAt(host, port)
        .bind(handler),
      10.seconds)
    // to allow binding to 0
    val actualPort = binding.localAddress.getPort
    system.log.info("Eventing testkit grpc-backend started at {}:{}", host, actualPort)
    service.port = actualPort
    service
  }

  final case class RunningSourceProbe(serviceName: String, source: EventSource)(
      outQueue: BoundedSourceQueue[SourceElem],
      val outSource: Source[SourceElem, NotUsed]) {
    private val log = LoggerFactory.getLogger(classOf[RunningSourceProbe])

    private def emitElement(element: SourceElem): Unit = {
      log.debug("Emitting message {}", element)
      outQueue.offer(element) match {
        case QueueOfferResult.Enqueued    => // goodie
        case QueueOfferResult.Failure(ex) => throw ex
        case QueueOfferResult.Dropped     => throw new AssertionError("Element was dropped")
        case QueueOfferResult.QueueClosed => throw new AssertionError("Queue was closed")
      }
    }

    def emit(data: ByteString, metadata: SdkMetadata): Unit = {

      // FIXME maybe we could improve validation for metadata?
      def convertMetadataEntry(sdkMetadataEntry: SdkMetadataEntry): MetadataEntry = {
        val mde = MetadataEntry(sdkMetadataEntry.getKey)
        if (sdkMetadataEntry.isText) {
          mde.withStringValue(sdkMetadataEntry.getValue)
        } else {
          @nowarn("msg=deprecated")
          val binary = sdkMetadataEntry.getBinaryValue;
          mde.withBytesValue(ByteString.copyFrom(binary))
        }
      }

      val testKitMetadata =
        Metadata(metadata.iterator().asScala.map(convertMetadataEntry).toList)

      val subject = metadata.get("ce-subject").orElse("")
      log.debug(
        "Emitting from testkit to test broker, message with metadata={} with subject={}",
        testKitMetadata,
        subject)

      emitElement(SourceElem(Some(Message(data, Some(testKitMetadata))), subject))
    }

  }

  def metadataToSpi(metadata: Option[Metadata]): SpiMetadata =
    metadata.map(metadataToSpi).getOrElse(SpiMetadata.empty)

  def metadataToSpi(metadata: Metadata): SpiMetadata = {
    import kalix.protocol.component.MetadataEntry.Value
    val entries = metadata.entries.map(entry =>
      entry.value match {
        case Value.Empty              => new SpiMetadataEntry(entry.key, "")
        case Value.StringValue(value) => new SpiMetadataEntry(entry.key, value)
        case Value.BytesValue(value) =>
          new SpiMetadataEntry(entry.key, value.toStringUtf8) //FIXME binary not supported
      })
    new SpiMetadata(entries)
  }
}

/**
 * Implements the EventingTestKit protocol originally defined in proxy
 * protocols/testkit/src/main/protobuf/eventing_test_backend.proto
 */
final class EventingTestServiceImpl(system: ActorSystem[_], val host: String, var port: Int, serializer: JsonSerializer)
    extends EventingTestKit {

  private val log = LoggerFactory.getLogger(classOf[EventingTestServiceImpl])
  private implicit val sys: akka.actor.ActorSystem = system.classicSystem
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  private val topicDestinations = new ConcurrentHashMap[String, OutgoingMessagesImpl]()

  private val veSubscriptions = new ConcurrentHashMap[String, VeIncomingMessagesImpl]()
  private val esSubscriptions = new ConcurrentHashMap[String, IncomingMessagesImpl]()
  private val workflowSubscriptions = new ConcurrentHashMap[String, IncomingMessagesImpl]()
  private val streamSubscriptions = new ConcurrentHashMap[String, IncomingMessagesImpl]()
  private val topicSubscriptions = new ConcurrentHashMap[String, IncomingMessagesImpl]()

  override def getTopicIncomingMessages(topic: String): IncomingMessages = getTopicIncomingMessagesImpl(topic)

  private def getTopicIncomingMessagesImpl(topic: String): IncomingMessagesImpl =
    topicSubscriptions.computeIfAbsent(
      topic,
      _ => new IncomingMessagesImpl(sys.actorOf(Props[SourcesHolder](), "topic-holder-" + topic), serializer))

  override def getTopicOutgoingMessages(topic: String): OutgoingMessages = getTopicOutgoingMessagesImpl(topic)

  private def getTopicOutgoingMessagesImpl(topic: String): OutgoingMessagesImpl =
    topicDestinations.computeIfAbsent(topic, _ => new OutgoingMessagesImpl(TestProbe(), serializer))

  override def getKeyValueEntityIncomingMessages(componentId: String): IncomingMessages =
    getValueEntityIncomingMessagesImpl(componentId)

  private def getValueEntityIncomingMessagesImpl(componentId: String): VeIncomingMessagesImpl =
    veSubscriptions.computeIfAbsent(
      componentId,
      _ => new VeIncomingMessagesImpl(sys.actorOf(Props[SourcesHolder](), "ve-holder-" + componentId), serializer))

  override def getEventSourcedEntityIncomingMessages(componentId: String): IncomingMessages =
    getEventSourcedSubscriptionImpl(componentId)

  override def getWorkflowIncomingMessages(componentId: String): IncomingMessages =
    getWorkflowSubscriptionImpl(componentId)

  private def getEventSourcedSubscriptionImpl(componentId: String): IncomingMessagesImpl =
    esSubscriptions.computeIfAbsent(
      componentId,
      _ => new IncomingMessagesImpl(sys.actorOf(Props[SourcesHolder](), "es-holder-" + componentId), serializer))

  private def getWorkflowSubscriptionImpl(componentId: String): IncomingMessagesImpl =
    workflowSubscriptions.computeIfAbsent(
      componentId,
      _ => new IncomingMessagesImpl(sys.actorOf(Props[SourcesHolder](), "workflow-holder-" + componentId), serializer))

  override def getStreamIncomingMessages(service: String, streamId: String): IncomingMessages =
    getStreamIncomingMessagesImpl(service, streamId)

  private def getStreamIncomingMessagesImpl(service: String, streamId: String): IncomingMessagesImpl =
    streamSubscriptions.computeIfAbsent(
      service + "/" + streamId,
      _ =>
        new IncomingMessagesImpl(sys.actorOf(Props[SourcesHolder](), s"stream-holder-$service-$streamId"), serializer))

  final class ServiceImpl extends EventingTestKitService {
    override def emitSingle(in: EmitSingleCommand): Future[EmitSingleResult] = {
      log.debug("Receiving message from test broker: [{}]", in)

      in.destination.foreach { dest =>
        getTopicOutgoingMessagesImpl(dest.getTopic).destinationProbe.ref ! in
      }

      if (in.destination.isEmpty) {
        log.warn("Received a message without destination, ignoring. {}", in)
      }
      Future.successful(EmitSingleResult())
    }

    override def runSource(in: Source[RunSourceCommand, NotUsed]): Source[SourceElem, NotUsed] = {
      log.debug("Reading topic from test broker - runSource request started: {}", in)
      val runningSourcePromise = Promise[RunningSourceProbe]()

      in.watchTermination() { (_, fDone) =>
        fDone.onComplete {
          case Success(_)  => log.debug("runSource in completed")
          case Failure(ex) => log.error("runSource in failed", ex)
        }
      }.runWith(Sink.fold(None: Option[RunningSourceProbe]) {
        case (
              None,
              RunSourceCommand(
                RunSourceCommand.Command.Create(RunSourceCreate(serviceName, Some(eventSource), _)),
                _)) =>
          // proxy triggers probe creation through the create command
          log.debug(
            "runSource request got initial create command for service name [{}], source: [{}]",
            serviceName,
            eventSource)
          val (queue, source) = Source.queue[SourceElem](10).preMaterialize()
          val runningSourceProbe = RunningSourceProbe(serviceName, eventSource)(queue, source)
          eventSource.source match {
            case EventSource.Source.Empty => throw new IllegalStateException("not recognized empty eventing source")
            case EventSource.Source.Topic(topic) =>
              getTopicIncomingMessagesImpl(topic).addSourceProbe(runningSourceProbe)
            case EventSource.Source.EventSourcedEntity(componentId) =>
              getEventSourcedSubscriptionImpl(componentId).addSourceProbe(runningSourceProbe)
            case EventSource.Source.Workflow(componentId) =>
              getWorkflowSubscriptionImpl(componentId).addSourceProbe(runningSourceProbe)
            case EventSource.Source.ValueEntity(componentId) =>
              getValueEntityIncomingMessagesImpl(componentId).addSourceProbe(runningSourceProbe)
            case EventSource.Source.Direct(DirectSource(service, eventStreamId, _)) =>
              getStreamIncomingMessagesImpl(service, eventStreamId).addSourceProbe(runningSourceProbe)
          }
          runningSourcePromise.success(runningSourceProbe)
          Some(runningSourceProbe)

        case (s @ Some(_), RunSourceCommand(RunSourceCommand.Command.Ack(sourceAck), _)) =>
          log.debug("runSource request got ack [{}]", sourceAck)
          s
        case wat => throw new MatchError(s"Unexpected fold input: $wat")
      })

      Source
        .futureSource(runningSourcePromise.future.map { runningSourceProbe =>
          runningSourceProbe.outSource
        })
        .watchTermination() { (_, fDone) =>
          fDone.onComplete {
            case Success(_)  => log.debug("runSource out completed")
            case Failure(ex) => log.error("runSource out failed", ex)
          }
        }
        .mapMaterializedValue(_ => NotUsed)
    }

    override def eventStreamOut(in: Source[EventStreamOutCommand, NotUsed]): Source[EventStreamOutResult, NotUsed] =
      throw new UnsupportedOperationException("Feature not supported in the testkit yet")
  }
}

private[testkit] class IncomingMessagesImpl(val sourcesHolder: ActorRef, val serializer: JsonSerializer)
    extends IncomingMessages {

  def addSourceProbe(runningSourceProbe: RunningSourceProbe): Unit = {
    val addSource = sourcesHolder.ask(SourcesHolder.AddSource(runningSourceProbe))(5.seconds)
    Await.result(addSource, 10.seconds)
  }

  override def publish(message: ByteString): Unit =
    publish(message, SdkMetadata.EMPTY)

  override def publish(message: ByteString, metadata: SdkMetadata): Unit = {
    val addSource = sourcesHolder.ask(SourcesHolder.Publish(message, metadata))(5.seconds)
    Await.result(addSource, 5.seconds)
  }

  override def publish(message: Array[Byte]): Unit =
    publish(message, SdkMetadata.EMPTY)

  override def publish(message: Array[Byte], metadata: SdkMetadata): Unit = {
    val addSource = sourcesHolder.ask(SourcesHolder.Publish(ByteString.copyFrom(message), metadata))(5.seconds)
    Await.result(addSource, 5.seconds)
  }

  override def publish(message: TestKitMessage[_]): Unit = message.getPayload match {
    case javaPb: GeneratedMessageV3 => publish(javaPb.toByteString, message.getMetadata)
    case scalaPb: GeneratedMessage  => publish(scalaPb.toByteString, message.getMetadata)
    case str: String                => publish(ByteString.copyFromUtf8(str), message.getMetadata)
    case _ =>
      val encodedMsg = JsonSupport.getObjectMapper
        .writerFor(message.getPayload.getClass)
        .writeValueAsBytes(message.getPayload)
      publish(ByteString.copyFrom(encodedMsg), message.getMetadata)
  }

  override def publish[T](message: T, subject: String): Unit = {
    val md = defaultMetadata(message, subject, serializer)
    publish(TestKitMessageImpl(message, md))
  }

  override def publish(message: JList[TestKitMessage[_]]): Unit =
    message.asScala.foreach(m => publish(m))

  override def publishDelete(subject: String): Unit = throw new IllegalStateException(
    "Publishing a delete message is supported only for ValueEntity messages.")
}

private[testkit] class VeIncomingMessagesImpl(
    override val sourcesHolder: ActorRef,
    override val serializer: JsonSerializer)
    extends IncomingMessagesImpl(sourcesHolder, serializer) {

  override def publishDelete(subject: String): Unit = {
    publish(
      ByteString.EMPTY,
      TestKitMessageImpl.defaultMetadata(
        subject,
        "application/vnd.kalix.delete",
        "type.googleapis.com/google.protobuf.Empty"))
  }

}

private[testkit] class OutgoingMessagesImpl(
    private[testkit] val destinationProbe: TestProbe,
    protected val serializer: JsonSerializer)
    extends OutgoingMessages {
  import EventingTestKitImpl.metadataToSpi

  val DefaultTimeout: time.Duration = time.Duration.ofSeconds(3)

  private val log = LoggerFactory.getLogger(classOf[OutgoingMessagesImpl])

  override def expectNone(): Unit = expectNone(DefaultTimeout)

  override def expectNone(timeout: time.Duration): Unit = destinationProbe.expectNoMessage(timeout.toScala)

  override def expectOneRaw(): TestKitMessage[ByteString] = expectOneRaw(DefaultTimeout)

  override def expectOneRaw(timeout: time.Duration): TestKitMessage[ByteString] = {
    val msg = expectMsgInternal(destinationProbe, timeout)
    TestKitMessageImpl.ofProtocolMessage(msg.getMessage)
  }

  override def expectOne(): TestKitMessage[_] = expectOne(DefaultTimeout)

  override def expectOne(timeout: time.Duration): TestKitMessage[_] = {
    val msg = expectMsgInternal(destinationProbe, timeout)
    anyFromMessage(msg.getMessage)
  }

  override def expectOneTyped[T](clazz: Class[T]): TestKitMessage[T] =
    expectOneTyped(clazz, DefaultTimeout)

  override def expectOneTyped[T](clazz: Class[T], timeout: time.Duration): TestKitMessage[T] = {
    val msg = expectMsgInternal(destinationProbe, timeout, Some(clazz))
    val metadata = MetadataImpl.of(metadataToSpi(msg.getMessage.getMetadata))
    // FIXME don't use proto
    val scalaPb = ScalaPbAny(typeUrlFor(metadata), msg.getMessage.payload)

    val decodedMsg = if (serializer.isJsonContentType(typeUrlFor(metadata))) {
      JsonSupport.getObjectMapper
        .readerFor(clazz)
        .readValue(msg.getMessage.payload.toByteArray)
    } else {
      val anySupport = new AnySupport(Array(), getClass.getClassLoader)
      anySupport.decodeMessage(scalaPb)
    }

    val concreteType = TestKitMessageImpl.expectType(decodedMsg, clazz)
    TestKitMessageImpl(concreteType, metadata)
  }

  private def anyFromMessage(m: kalix.testkit.protocol.eventing_test_backend.Message): TestKitMessage[_] = {
    val metadata = MetadataImpl.of(metadataToSpi(m.metadata))
    val anyMsg = if (AnySupport.isJsonTypeUrl(typeUrlFor(metadata))) {
      m.payload.toStringUtf8 // FIXME isn't this strange?
    } else {
      val anySupport = new AnySupport(Array(), getClass.getClassLoader)
      anySupport.decodeMessage(ScalaPbAny(typeUrlFor(metadata), m.payload))
    }
    TestKitMessageImpl(anyMsg, metadata)
  }

  private def typeUrlFor(metadata: MetadataImpl): String = {
    val ceType = metadata.get("ce-type").toScala
    val contentType = metadata.get("Content-Type").toScala

    (ceType, contentType) match {
      case (_, Some("text/plain; charset=utf-8")) => "type.kalix.io/string"
      case (_, Some("application/octet-stream"))  => "type.kalix.io/bytes"
      case (Some(t), Some("application/json"))    => s"json.akka.io/$t"
      case (Some(t), _)                           => s"type.googleapis.com/$t"
      case (t, ct) =>
        log.warn(s"Could not extract typeUrl from ce-type=$t content-type=$ct")
        ""
    }
  }

  override def expectN(): JList[TestKitMessage[_]] =
    expectN(Int.MaxValue, DefaultTimeout)

  override def expectN(total: Int): JList[TestKitMessage[_]] =
    expectN(total, DefaultTimeout)

  override def expectN(total: Int, timeout: time.Duration): JList[TestKitMessage[_]] = {
    destinationProbe
      .receiveN(total, timeout.toScala)
      .map { case cmd: EmitSingleCommand =>
        anyFromMessage(cmd.getMessage)
      }
      .asJava
  }

  override def clear(): JList[TestKitMessage[_]] = {
    destinationProbe
      .receiveWhile(idle = 50.millisecond) { case cmd: EmitSingleCommand =>
        anyFromMessage(cmd.getMessage)
      }
      .asJava
  }
}

private[testkit] case class TestKitMessageImpl[P](payload: P, metadata: SdkMetadata) extends TestKitMessage[P] {
  override def getPayload: P = payload
  override def getMetadata: SdkMetadata = metadata
  override def expectType[T](clazz: Class[T]): T = TestKitMessageImpl.expectType(payload, clazz)
}

private[testkit] object TestKitMessageImpl {
  import EventingTestKitImpl.metadataToSpi

  def ofProtocolMessage(m: kalix.testkit.protocol.eventing_test_backend.Message): TestKitMessage[ByteString] = {
    val metadata = MetadataImpl.of(metadataToSpi(m.metadata))
    TestKitMessageImpl[ByteString](m.payload, metadata).asInstanceOf[TestKitMessage[ByteString]]
  }

  def defaultMetadata(message: Any, subject: String, serializer: JsonSerializer): SdkMetadata = {
    val (contentType, ceType) = message match {
      case pbMsg: GeneratedMessageV3 =>
        val desc = pbMsg.getDescriptorForType
        (s"application/protobuf;proto=${desc.getFullName}", desc.getName)
      case pbMsg: GeneratedMessage =>
        val desc = pbMsg.companion.javaDescriptor
        (s"application/protobuf;proto=${desc.getFullName}", desc.getName)
      case _: String =>
        ("text/plain; charset=utf-8", "")
      case _ =>
        ("application/json", serializer.stripJsonContentTypePrefix(serializer.contentTypeFor(message.getClass)))
    }

    defaultMetadata(subject, contentType, ceType)
  }

  def defaultMetadata(subject: String, contentType: String, ceType: String): SdkMetadata = {
    SdkMetadata.EMPTY
      .add("Content-Type", contentType)
      .add("ce-specversion", "1.0")
      .add("ce-id", UUID.randomUUID().toString)
      .add("ce-subject", subject)
      .add("ce-type", ceType)
      .add("ce-source", classOf[EventingTestKit].getName)
  }

  def expectType[T](payload: Any, clazz: Class[T]): T = {
    val bt = BoxedType(clazz)
    payload match {
      case m if bt.isInstance(m) => m.asInstanceOf[T]
      case m: String             => JsonSupport.getObjectMapper.readerFor(clazz).readValue(m)
      case m                     => throw new AssertionError(s"Expected $clazz, found ${m.getClass} ($m)")
    }
  }

  // converting the internal assertion error into a user-friendly one
  def expectMsgInternal(
      destinationProbe: TestProbe,
      timeout: time.Duration,
      clazz: Option[Class[_]] = None): EmitSingleCommand = {
    try {
      destinationProbe.expectMsgType[EmitSingleCommand](timeout.toScala)
    } catch {
      case _: AssertionError =>
        val typeMsg = clazz.map(" of type " + _.getName).getOrElse("")
        throw new AssertionError(s"timeout (${timeout.toScala}) while waiting for message$typeMsg")
    }
  }
}
