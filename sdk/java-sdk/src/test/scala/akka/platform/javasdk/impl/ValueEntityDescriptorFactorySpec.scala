/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.impl

import scala.jdk.CollectionConverters.CollectionHasAsScala
import kalix.JwtMethodOptions.JwtMethodMode
import kalix.JwtServiceOptions.JwtServiceMode
import akka.platform.spring.testmodels.valueentity.Counter
import akka.platform.spring.testmodels.valueentity.ValueEntitiesTestModels.InvalidValueEntityWithOverloadedCommandHandler
import akka.platform.spring.testmodels.valueentity.ValueEntitiesTestModels.ValueEntityWithMethodLevelAcl
import akka.platform.spring.testmodels.valueentity.ValueEntitiesTestModels.ValueEntityWithMethodLevelJwt
import akka.platform.spring.testmodels.valueentity.ValueEntitiesTestModels.ValueEntityWithServiceLevelAcl
import akka.platform.spring.testmodels.valueentity.ValueEntitiesTestModels.ValueEntityWithServiceLevelJwt
import org.scalatest.wordspec.AnyWordSpec

class ValueEntityDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "ValueEntity descriptor factory" should {
    "validate a ValueEntity must be declared as public" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[NotPublicComponents.NotPublicValueEntity]).failIfInvalid
      }.getMessage should include(
        "NotPublicValueEntity is not marked with `public` modifier. Components must be public.")
    }

    "generate mappings for a Value Entity" in {
      assertDescriptor[Counter] { desc =>

        val increaseMethod = desc.commandHandlers("Increase")
        val increaseUrl = findHttpRule(desc, increaseMethod.grpcMethodName).getPost
        increaseUrl shouldBe "/akka/v1.0/entity/ve-counter/{id}/increase"

        val randomIncreaseMethod = desc.commandHandlers("RandomIncrease")
        val randomIncreaseUrl = findHttpRule(desc, randomIncreaseMethod.grpcMethodName).getPost
        randomIncreaseUrl shouldBe "/akka/v1.0/entity/ve-counter/{id}/randomIncrease"

        val getMethod = desc.commandHandlers("Get")
        val getUrl = findHttpRule(desc, getMethod.grpcMethodName).getGet
        getUrl shouldBe "/akka/v1.0/entity/ve-counter/{id}/get"
      }
    }

    "generate ACL annotations at service level" in {
      assertDescriptor[ValueEntityWithServiceLevelAcl] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate ACL annotations at method level" in {
      assertDescriptor[ValueEntityWithMethodLevelAcl] { desc =>
        val extension = findKalixMethodOptions(desc, "CreateEntity")
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate descriptor for ValueEntity with service level JWT annotation" in {
      assertDescriptor[ValueEntityWithServiceLevelJwt] { desc =>
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

    "generate descriptor for ValueEntity with method level JWT annotation" in {
      assertDescriptor[ValueEntityWithMethodLevelJwt] { desc =>
        val jwtOption = findKalixMethodOptions(desc, "CreateEntity").getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "c"
        jwtOption.getBearerTokenIssuer(1) shouldBe "d"
        jwtOption.getValidate(0) shouldBe JwtMethodMode.BEARER_TOKEN
        val Seq(claim1, claim2) = jwtOption.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "method-admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}"
      }
    }

    "not allow overloaded command handlers" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[InvalidValueEntityWithOverloadedCommandHandler]).failIfInvalid
      }.getMessage should include(
        "InvalidValueEntityWithOverloadedCommandHandler has 2 command handler methods named 'createEntity'. Command handlers must have unique names.")
    }

  }
}
