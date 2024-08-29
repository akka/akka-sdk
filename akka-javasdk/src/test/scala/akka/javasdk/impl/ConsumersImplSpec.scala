/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import akka.Done
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.javasdk.JsonSupport
import akka.javasdk.consumer.ConsumerContext
import akka.javasdk.consumer.ReflectiveConsumerProvider
import akka.javasdk.impl.JsonMessageCodec
import akka.javasdk.impl.MessageCodec
import akka.javasdk.impl.ProxyInfoHolder
import akka.javasdk.impl.action.ActionsImpl
import akka.javasdk.impl.consumer.ConsumerService
import akka.javasdk.impl.telemetry.Telemetry
import JsonSupport.decodeJson
import akka.javasdk.eventsourcedentity.OldTestESEvent.OldEvent1
import akka.javasdk.eventsourcedentity.OldTestESEvent.OldEvent2
import akka.javasdk.eventsourcedentity.OldTestESEvent.OldEvent3
import akka.javasdk.eventsourcedentity.TestESEvent
import TestESEvent.Event4
import akka.javasdk.timedaction.TestESSubscription
import akka.javasdk.timedaction.TestTracing
import akka.runtime.sdk.spi.DeferredRequest
import akka.runtime.sdk.spi.TimerClient
import com.google.protobuf.any.Any.toJavaProto
import com.google.protobuf.any.{ Any => ScalaPbAny }
import kalix.protocol.action.ActionCommand
import kalix.protocol.action.ActionResponse
import kalix.protocol.action.Actions
import kalix.protocol.component.Metadata
import kalix.protocol.component.MetadataEntry
import kalix.protocol.component.Reply
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Inside
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.slf4j.LoggerFactory

class ConsumersImplSpec
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with Inside
    with OptionValues
    with ScalaFutures {

  private val classicSystem = system.toClassic

  def create(
      provider: ReflectiveConsumerProvider[_],
      messageCodec: MessageCodec,
      tracingCollector: String = ""): Actions = {
    val actionFactory: ConsumerFactory = ctx => provider.newRouter(ctx)
    val service = new ConsumerService(actionFactory, provider.serviceDescriptor(), Array(), messageCodec, None)

    val services = Map(provider.serviceDescriptor().getFullName -> service)

    //setting tracing as disabled, emulating that is discovered from the proxy.
    ProxyInfoHolder(system).overrideTracingCollectorEndpoint(tracingCollector)

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
      classicSystem.dispatcher)
  }

  "The consumer service" should {
    "check event migration for subscription" in {
      val jsonMessageCodec = new JsonMessageCodec()
      val consumerProvider = ReflectiveConsumerProvider.of(
        classOf[TestESSubscription],
        jsonMessageCodec,
        (_: ConsumerContext) => new TestESSubscription)

      val service = create(consumerProvider, jsonMessageCodec)
      val serviceName = consumerProvider.serviceDescriptor().getFullName

      val event1 = jsonMessageCodec.encodeScala(new OldEvent1("state"))
      val reply1 = service.handleUnary(toActionCommand(serviceName, event1)).futureValue
      //ignore event1
      reply1.response shouldBe ActionResponse.Response.Empty

      val event2 = new JsonMessageCodec().encodeScala(new OldEvent2(123))
      val reply2 = service.handleUnary(toActionCommand(serviceName, event2)).futureValue
      inside(reply2.response) { case ActionResponse.Response.Reply(Reply(Some(payload), _, _)) =>
        decodeJson(classOf[Integer], toJavaProto(payload)) shouldBe 321 //migration reverts numbers
      }

      val event3 = new JsonMessageCodec().encodeScala(new OldEvent3(true))
      val reply3 = service.handleUnary(toActionCommand(serviceName, event3)).futureValue
      inside(reply3.response) { case ActionResponse.Response.Reply(Reply(Some(payload), _, _)) =>
        decodeJson(classOf[Boolean], toJavaProto(payload)) shouldBe true
      }

      val event4OldVersionNumber = JsonSupport.encodeJson(new Event4("value"), classOf[Event4].getName + "#1")
      val event4 =
        new JsonMessageCodec().encodeScala(event4OldVersionNumber)
      val reply4 = service.handleUnary(toActionCommand(serviceName, event4)).futureValue
      inside(reply4.response) { case ActionResponse.Response.Reply(Reply(Some(payload), _, _)) =>
        decodeJson(classOf[String], toJavaProto(payload)) shouldBe "value-v2" //-v2 from migration
      }
    }

    "inject traces correctly into metadata and keeps trace_id in MDC" in {
      val jsonMessageCodec = new JsonMessageCodec()
      val consumerProvider =
        ReflectiveConsumerProvider.of(classOf[TestTracing], jsonMessageCodec, (_: ConsumerContext) => new TestTracing)

      val service = create(consumerProvider, jsonMessageCodec, "http://localhost:1111")
      val serviceName = consumerProvider.serviceDescriptor().getFullName

      val cmd1 = ScalaPbAny.fromJavaProto(JsonSupport.encodeJson(new TestESEvent.Event2(123)))

      val traceParent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
      val metadata = Metadata(Seq(MetadataEntry("traceparent", MetadataEntry.Value.StringValue(traceParent))))
      val expectedMDC = Map(Telemetry.TRACE_ID -> "0af7651916cd43dd8448eb211c80319c")
      val reply1 =
        LoggingTestKit.empty.withMdc(expectedMDC).expect {
          service.handleUnary(ActionCommand(serviceName, "Consume", Some(cmd1), Some(metadata))).futureValue
        }

      inside(reply1.response) { case ActionResponse.Response.Reply(Reply(Some(payload), _, _)) =>
        val tp = decodeJson(classOf[String], toJavaProto(payload))
        tp should not be "not-found"
        tp should include("0af7651916cd43dd8448eb211c80319c") // trace id should be propagated
        (tp should not).include("b7ad6b7169203331") // new span id should be generated
      }

      val log = LoggerFactory.getLogger(classOf[ConsumersImplSpec])
      LoggingTestKit.empty.withMdc(Map.empty).expect {
        Future {
          log.info("checking the MDC is empty")
        }(ExecutionContext.parasitic) //parasitic to checking that in the same thread there's no MDC any more
      }
    }
  }

  private def toActionCommand(serviceName: String, event1: ScalaPbAny) = {
    ActionCommand(serviceName, "KalixSyntheticMethodOnESEs", Some(event1))
  }

}
