/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.InvalidComponentException
import akka.javasdk.impl.Validations

import scala.jdk.CollectionConverters.CollectionHasAsScala
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import kalix.JwtMethodOptions.JwtMethodMode
import kalix.JwtServiceOptions.JwtServiceMode
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflow
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithAcl
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithMethodLevelAcl
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithMethodLevelJWT
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithServiceLevelJWT
import org.scalatest.wordspec.AnyWordSpec

class WorkflowEntityDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "Workflow descriptor factory" should {
    "validate a Workflow must be declared as public" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[NotPublicComponents.NotPublicWorkflow]).failIfInvalid
      }.getMessage should include("NotPublicWorkflow is not marked with `public` modifier. Components must be public.")
    }

    "generate mappings for a Workflow with entity ids in path" in {
      assertDescriptor[TransferWorkflow] { desc =>
        val startTransferMethod = desc.commandHandlers("StartTransfer")
        val startTransferUrl = findHttpRule(desc, startTransferMethod.grpcMethodName).getPost
        startTransferUrl shouldBe "/akka/v1.0/workflow/transfer-workflow/{id}/startTransfer"

        val fieldKey = "id"
        assertRequestFieldJavaType(startTransferMethod, fieldKey, JavaType.STRING)
        assertEntityIdField(startTransferMethod, fieldKey)
        assertRequestFieldJavaType(startTransferMethod, "json_body", JavaType.MESSAGE)

        val getStateMethod = desc.commandHandlers("GetState")
        val getStateUrl = findHttpRule(desc, getStateMethod.grpcMethodName).getGet
        getStateUrl shouldBe "/akka/v1.0/workflow/transfer-workflow/{id}/getState"
      }
    }

    "generate mappings for a Workflow with workflow keys in path and method level JWT annotation" in {
      assertDescriptor[WorkflowWithMethodLevelJWT] { desc =>
        val method = desc.commandHandlers("StartTransfer")
        val fieldKey = "id"
        assertRequestFieldJavaType(method, fieldKey, JavaType.STRING)
        assertEntityIdField(method, fieldKey)
        assertRequestFieldJavaType(method, "json_body", JavaType.MESSAGE)

        val jwtOption = findKalixMethodOptions(desc, method.grpcMethodName).getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "a"
        jwtOption.getBearerTokenIssuer(1) shouldBe "b"
        jwtOption.getValidate(0) shouldBe JwtMethodMode.BEARER_TOKEN

        val Seq(claim1, claim2) = jwtOption.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "method-admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}.kalix.io"
      }
    }

    "generate mappings for a Workflow with workflow keys in path and service level JWT annotation" in {
      assertDescriptor[WorkflowWithServiceLevelJWT] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val jwtOption = extension.getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "c"
        jwtOption.getBearerTokenIssuer(1) shouldBe "d"
        jwtOption.getValidate shouldBe JwtServiceMode.BEARER_TOKEN

        val Seq(claim1, claim2) = jwtOption.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}"
      }
    }

    "generate ACL annotations at service level" in {
      assertDescriptor[WorkflowWithAcl] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate ACL annotations at method level" in {
      assertDescriptor[WorkflowWithMethodLevelAcl] { desc =>
        val extension = findKalixMethodOptions(desc, "StartTransfer")
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }
  }

}
