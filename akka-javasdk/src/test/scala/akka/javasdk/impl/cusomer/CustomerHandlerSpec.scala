/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.cusomer

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import akka.Done
import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.javasdk.consumer.Consumer
import akka.javasdk.consumer.MessageEnvelope
import akka.javasdk.impl.AnySupport
import akka.javasdk.impl.ConsumerFactory
import akka.javasdk.impl.ProxyInfoHolder
import akka.javasdk.impl.action.ActionsImpl
import akka.javasdk.impl.consumer.ConsumerEffectImpl
import akka.javasdk.impl.consumer.ConsumerRouter
import akka.javasdk.impl.consumer.ConsumerService
import akka.runtime.sdk.spi.DeferredRequest
import akka.runtime.sdk.spi.TimerClient
import com.google.protobuf
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.javasdk.actionspec.ActionspecApi
import kalix.protocol.action.ActionCommand
import kalix.protocol.action.ActionResponse
import kalix.protocol.action.Actions
import kalix.protocol.component.Reply
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class CustomerHandlerSpec
    extends ScalaTestWithActorTestKit
    with LogCapturing
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside
    with OptionValues {

  private val classicSystem = system.toClassic

  private val serviceDescriptor =
    ActionspecApi.getDescriptor.findServiceByName("ActionSpecService")
  private val serviceName = serviceDescriptor.getFullName
  private val anySupport = new AnySupport(Array(ActionspecApi.getDescriptor), this.getClass.getClassLoader)

  def create(handler: ConsumerRouter[_]): Actions = {
    val actionFactory: ConsumerFactory = _ => handler
    val service = new ConsumerService(actionFactory, serviceDescriptor, Array(), anySupport, None)

    val services = Map(serviceName -> service)

    //setting tracing as disabled, emulating that is discovered from the proxy.
    ProxyInfoHolder(system).overrideTracingCollectorEndpoint("")

    new ActionsImpl(
      classicSystem,
      services,
      new TimerClient {
        // Not exercised here
        override def startSingleTimer(
            name: String,
            delay: FiniteDuration,
            maxRetries: Int,
            deferredRequest: DeferredRequest): Future[Done] = ???
        override def removeTimer(name: String): Future[Done] = ???
      },
      system.executionContext)
  }

  "The action service" should {
    "invoke unary commands" in {
      val service = create(new AbstractHandler {

        override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Consumer.Effect =
          createProduceEffect("out: " + extractInField(message))
      })

      val reply =
        Await.result(service.handleUnary(ActionCommand(serviceName, "Unary", createInPayload("in"))), 10.seconds)

      inside(reply.response) { case ActionResponse.Response.Reply(Reply(payload, _, _)) =>
        extractOutField(payload) should ===("out: in")
      }
    }

    "turn thrown unary command handler exceptions into failure responses" in {
      val service = create(new AbstractHandler {

        override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Consumer.Effect =
          throw new RuntimeException("boom")
      })

      val reply =
        LoggingTestKit
          .error("Failure during handling of command")
          .expect {
            Await.result(service.handleUnary(ActionCommand(serviceName, "Unary", createInPayload("in"))), 10.seconds)
          }

      inside(reply.response) { case ActionResponse.Response.Failure(fail) =>
        fail.description should startWith("Unexpected error")
      }
    }

    "allow async ignore" in {
      val service = create(new AbstractHandler {

        override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Consumer.Effect = {
          createAsyncEffect(Future.successful(createIgnoreEffect()))
        }
      })

      val reply =
        Await.result(service.handleUnary(ActionCommand(serviceName, "Unary", createInPayload("in"))), 10.seconds)

      reply match {
        case ActionResponse(ActionResponse.Response.Empty, _, _) =>
        case e                                                   => fail(s"Unexpected response: $e")
      }
    }

    "turn async failure into failure response" in {
      val service = create(new AbstractHandler {

        override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Consumer.Effect =
          createAsyncEffect(Future.failed(new RuntimeException("boom")))
      })

      val reply =
        LoggingTestKit.error("Failure during handling of command").expect {
          Await.result(service.handleUnary(ActionCommand(serviceName, "Unary", createInPayload("in"))), 10.seconds)
        }
      inside(reply.response) { case ActionResponse.Response.Failure(fail) =>
        fail.description should startWith("Unexpected error")
      }
    }

  }

  private def createProduceEffect(msg: String): Consumer.Effect =
    ConsumerEffectImpl.ReplyEffect(ActionspecApi.Out.newBuilder().setField(msg).build(), None)

  private def createIgnoreEffect(): Consumer.Effect =
    ConsumerEffectImpl.IgnoreEffect

  private def createAsyncEffect(future: Future[Consumer.Effect]): Consumer.Effect =
    ConsumerEffectImpl.AsyncEffect(future)

  private def extractInField(message: MessageEnvelope[Any]) =
    message.payload().asInstanceOf[ActionspecApi.In].getField

  private def createInPayload(field: String) =
    Some(ScalaPbAny.fromJavaProto(protobuf.Any.pack(ActionspecApi.In.newBuilder().setField(field).build())))

  private def extractOutField(payload: Option[ScalaPbAny]) =
    ScalaPbAny.toJavaProto(payload.value).unpack(classOf[ActionspecApi.Out]).getField

  class TestConsumer extends Consumer

  private abstract class AbstractHandler extends ConsumerRouter[TestConsumer](new TestConsumer) {
    override def handleUnary(commandName: String, message: MessageEnvelope[Any]): Consumer.Effect =
      ???

//    def handleStreamedOut(commandName: String, message: MessageEnvelope[Any]): Source[Consumer.Effect, NotUsed] = ???
//
//    override def handleStreamedIn(commandName: String, stream: Source[MessageEnvelope[Any], NotUsed]): Consumer.Effect =
//      ???

//    def handleStreamed(
//        commandName: String,
//        stream: Source[MessageEnvelope[Any], NotUsed]): Source[Consumer.Effect, NotUsed] = ???
  }

}
