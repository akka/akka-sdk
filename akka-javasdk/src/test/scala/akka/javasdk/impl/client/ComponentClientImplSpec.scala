/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import scala.concurrent.ExecutionContext

import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.serialization.Serializer
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.context.{ Context => OtelContext }
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ComponentClientImplSpec extends AnyWordSpec with Matchers with OptionValues {

  private def componentClient(telemetryContext: Option[OtelContext]): ComponentClientImpl =
    ComponentClientImpl(null, null, new Serializer, Map.empty, None, telemetryContext)(ExecutionContext.global, null)

  // The baggage header of the calls that the client makes.
  private def baggageHeader(client: akka.javasdk.client.ComponentClient): Option[String] =
    client
      .asInstanceOf[ComponentClientImpl]
      .telemetryContext
      .flatMap(context => MetadataImpl.Empty.withTelemetryContext(context).getScala("baggage"))

  "ComponentClient.withExternalId" should {

    "send the external id in the baggage of its calls" in {
      val client = componentClient(None).withExternalId("call-1")

      baggageHeader(client).value shouldBe "akka.external.id=call-1"
    }

    "keep the other baggage of the telemetry context" in {
      val context = OtelContext.root().`with`(Baggage.builder().put("akka.flow.id", "flow-1").build())

      val client = componentClient(Some(context)).withExternalId("call-1")

      baggageHeader(client).value.split(',').toSet shouldBe Set("akka.flow.id=flow-1", "akka.external.id=call-1")
    }

    "keep an external id that the telemetry context already carries" in {
      val context = OtelContext.root().`with`(Baggage.builder().put("akka.external.id", "call-1").build())

      val client = componentClient(Some(context)).withExternalId("call-2")

      baggageHeader(client).value shouldBe "akka.external.id=call-1"
    }

    "leave the client it is called on without an external id" in {
      val client = componentClient(None)
      client.withExternalId("call-1")

      baggageHeader(client) shouldBe None
    }

    "reject a null, empty or too long external id" in {
      val client = componentClient(None)

      intercept[NullPointerException](client.withExternalId(null))
      intercept[IllegalArgumentException](client.withExternalId(""))
      intercept[IllegalArgumentException](client.withExternalId("x" * 257))
      client.withExternalId("x" * 256)
    }
  }
}
