/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import java.util.Optional
import scala.util.control.NonFatal
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.impl.AbstractContext
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.Service
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.telemetry.Telemetry
import akka.javasdk.view.TableUpdater
import akka.javasdk.view.UpdateContext
import akka.javasdk.view.View
import akka.stream.scaladsl.Source
import kalix.protocol.{ view => pv }
import com.google.protobuf.any.{ Any => ScalaPbAny }
import org.slf4j.LoggerFactory
import org.slf4j.MDC

/**
 * INTERNAL API
 */
@InternalApi
final class ViewService[V <: View](
    viewClass: Class[_],
    messageCodec: JsonMessageCodec,
    wiredInstance: Class[TableUpdater[AnyRef]] => TableUpdater[AnyRef])
    extends Service(viewClass, pv.Views.name, messageCodec) {

  private def viewUpdaterFactories(): Set[TableUpdater[AnyRef]] = {
    val updaterClasses = viewClass.getDeclaredClasses.collect {
      case clz if Reflect.isViewTableUpdater(clz) => clz.asInstanceOf[Class[TableUpdater[AnyRef]]]
    }.toSet
    updaterClasses.map(updaterClass => wiredInstance(updaterClass))
  }
  def createRouter(): ReflectiveViewMultiTableRouter = {
    val viewUpdaters = viewUpdaterFactories()
      .map { updater =>
        val anyRefUpdater: TableUpdater[AnyRef] = updater
        anyRefUpdater.getClass.asInstanceOf[Class[TableUpdater[AnyRef]]] -> anyRefUpdater
      }
      .toMap[Class[TableUpdater[AnyRef]], TableUpdater[AnyRef]]
    new ReflectiveViewMultiTableRouter(viewUpdaters, componentDescriptor.commandHandlers)
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object ViewsImpl {
  private val log = LoggerFactory.getLogger(classOf[ViewsImpl])
}

/**
 * INTERNAL API
 */
@InternalApi
final class ViewsImpl(_services: Map[String, ViewService[_]], sdkDispatcherName: String) extends pv.Views {
  import ViewsImpl.log

  private final val services = _services.iterator.toMap

  /**
   * Handle a full duplex streamed session. One stream will be established per incoming message to the view service.
   *
   * The first message is ReceiveEvent and contain the request metadata, including the service name and command name.
   */
  override def handle(in: akka.stream.scaladsl.Source[pv.ViewStreamIn, akka.NotUsed])
      : akka.stream.scaladsl.Source[pv.ViewStreamOut, akka.NotUsed] =
    // FIXME: see runtime issues #207 and #209
    // It is currently only implemented to support one request (ReceiveEvent) with one response (Upsert).
    // The intention, and reason for full-duplex streaming, is that there should be able to have an interaction
    // with two main types of operations, loads, and updates, and with
    // each load there is an associated continuation, which in turn may return more operations, including more loads,
    // and so on recursively.
    in.prefixAndTail(1)
      .flatMapConcat {
        case (Seq(pv.ViewStreamIn(pv.ViewStreamIn.Message.Receive(receiveEvent), _)), _) =>
          services.get(receiveEvent.serviceName) match {
            case Some(service) =>
              // FIXME should we really create a new handler instance per incoming command ???
              val handler = service.createRouter()

              val state: Option[Any] =
                receiveEvent.bySubjectLookupResult.flatMap(row =>
                  row.value.map(scalaPb => service.messageCodec.decodeMessage(scalaPb)))

              val commandName = receiveEvent.commandName
              val msg = service.messageCodec.decodeMessage(receiveEvent.payload.get)
              val metadata = MetadataImpl.of(receiveEvent.metadata.map(_.entries.toVector).getOrElse(Nil))
              val addedToMDC = metadata.traceId match {
                case Some(traceId) =>
                  MDC.put(Telemetry.TRACE_ID, traceId)
                  true
                case None => false
              }
              val context = new UpdateContextImpl(commandName, metadata)

              val effect =
                try {
                  handler._internalHandleUpdate(state, msg, context)
                } catch {
                  case NonFatal(error) =>
                    log.error(s"View updater for view [${service.componentId}] threw an exception", error)
                    throw ViewException(
                      service.componentId,
                      context,
                      s"View unexpected failure: ${error.getMessage}",
                      Some(error))
                } finally {
                  if (addedToMDC) MDC.remove(Telemetry.TRACE_ID)
                }

              effect match {
                case ViewEffectImpl.Update(newState) =>
                  if (newState == null) {
                    log.error(
                      s"View updater tried to set row state to null, not allowed [${service.componentId}] threw an exception")
                    throw ViewException(
                      service.componentId,
                      context,
                      "updateState with null state is not allowed.",
                      None)
                  }
                  val serializedState = ScalaPbAny.fromJavaProto(service.messageCodec.encodeJava(newState))
                  val upsert = pv.Upsert(Some(pv.Row(value = Some(serializedState))))
                  val out = pv.ViewStreamOut(pv.ViewStreamOut.Message.Upsert(upsert))
                  Source.single(out)
                case ViewEffectImpl.Delete =>
                  val delete = pv.Delete()
                  val out = pv.ViewStreamOut(pv.ViewStreamOut.Message.Delete(delete))
                  Source.single(out)
                case ViewEffectImpl.Ignore =>
                  // ignore incoming event
                  val upsert = pv.Upsert(None)
                  val out = pv.ViewStreamOut(pv.ViewStreamOut.Message.Upsert(upsert))
                  Source.single(out)
              }

            case None =>
              val errMsg = s"Unknown service: ${receiveEvent.serviceName}"
              log.error(errMsg)
              Source.failed(new RuntimeException(errMsg))
          }

        case (Seq(), _) =>
          log.warn("View stream closed before init.")
          Source.empty[pv.ViewStreamOut]

        case (Seq(pv.ViewStreamIn(other, _)), _) =>
          val errMsg =
            s"Kalix protocol failure: expected ReceiveEvent message, but got ${other.getClass.getName}"
          Source.failed(new RuntimeException(errMsg))
      }
      .async(sdkDispatcherName)

  private final class UpdateContextImpl(override val eventName: String, override val metadata: Metadata)
      extends AbstractContext
      with UpdateContext {

    override def eventSubject(): Optional[String] =
      if (metadata.isCloudEvent)
        metadata.asCloudEvent().subject()
      else
        Optional.empty()
  }

}
