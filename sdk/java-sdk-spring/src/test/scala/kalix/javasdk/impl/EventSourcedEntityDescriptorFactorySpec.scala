/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.impl

import scala.jdk.CollectionConverters.CollectionHasAsScala

import kalix.JwtMethodOptions.JwtMethodMode
import kalix.JwtServiceOptions.JwtServiceMode
import kalix.spring.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.CounterEventSourcedEntity
import kalix.spring.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.CounterEventSourcedEntityWithMethodLevelJWT
import kalix.spring.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.CounterEventSourcedEntityWithServiceLevelJWT
import kalix.spring.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EventSourcedEntityWithMethodLevelAcl
import kalix.spring.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EventSourcedEntityWithServiceLevelAcl
import org.scalatest.wordspec.AnyWordSpec

class EventSourcedEntityDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "EventSourced descriptor factory" should {

    "validate an ESE must be declared as public" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[NotPublicComponents.NotPublicEventSourced]).failIfInvalid
      }.getMessage should include(
        "NotPublicEventSourced is not marked with `public` modifier. Components must be public.")
    }

    "generate mappings for an Event Sourced" in {
      assertDescriptor[CounterEventSourcedEntity] { desc =>
        val method = desc.commandHandlers("GetInteger")
        val getIntegerUrl = findHttpRule(desc, method.grpcMethodName).getGet
        getIntegerUrl shouldBe "/akka/v1.0/entity/counter-entity/{id}/getInteger"

        val postMethod = desc.commandHandlers("ChangeInteger")
        val changeIntegerUrl = findHttpRule(desc, postMethod.grpcMethodName).getPost
        changeIntegerUrl shouldBe "/akka/v1.0/entity/counter-entity/{id}/changeInteger"
      }
    }

    "generate mappings for a Event Sourced with method level JWT annotation" in {
      assertDescriptor[CounterEventSourcedEntityWithMethodLevelJWT] { desc =>
        val method = desc.commandHandlers("GetInteger")
        val getIntegerUrl = findHttpRule(desc, method.grpcMethodName).getGet
        getIntegerUrl shouldBe "/akka/v1.0/entity/counter/{id}/getInteger"

        val jwtOption = findKalixMethodOptions(desc, method.grpcMethodName).getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "a"
        jwtOption.getBearerTokenIssuer(1) shouldBe "b"
        jwtOption.getValidate(0) shouldBe JwtMethodMode.BEARER_TOKEN

        val postMethod = desc.commandHandlers("ChangeInteger")
        val changeIntegerUrl = findHttpRule(desc, postMethod.grpcMethodName).getPost
        changeIntegerUrl shouldBe "/akka/v1.0/entity/counter/{id}/changeInteger"

        val jwtOption2 = findKalixMethodOptions(desc, postMethod.grpcMethodName).getJwt
        jwtOption2.getBearerTokenIssuer(0) shouldBe "c"
        jwtOption2.getBearerTokenIssuer(1) shouldBe "d"
        jwtOption2.getValidate(0) shouldBe JwtMethodMode.BEARER_TOKEN

        val Seq(claim1, claim2) = jwtOption2.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "method-admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}"
      }
    }

    "generate mappings for a Event Sourced with service level JWT annotation" in {
      assertDescriptor[CounterEventSourcedEntityWithServiceLevelJWT] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val jwtOption = extension.getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "a"
        jwtOption.getBearerTokenIssuer(1) shouldBe "b"
        jwtOption.getValidate shouldBe JwtServiceMode.BEARER_TOKEN

        val Seq(claim1, claim2) = jwtOption.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}.kalix.io"
      }
    }

    "generate ACL annotations at service level" in {
      assertDescriptor[EventSourcedEntityWithServiceLevelAcl] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate ACL annotations at method level" in {
      assertDescriptor[EventSourcedEntityWithMethodLevelAcl] { desc =>
        val extension = findKalixMethodOptions(desc, "CreateUser")
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

  }

}
