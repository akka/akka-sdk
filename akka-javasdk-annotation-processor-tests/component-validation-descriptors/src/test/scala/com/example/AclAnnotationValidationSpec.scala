/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeAclAnnotationValidationSpec extends AbstractAclAnnotationValidationSpec(CompileTimeValidation)
class RuntimeAclAnnotationValidationSpec extends AbstractAclAnnotationValidationSpec(RuntimeValidation)

abstract class AbstractAclAnnotationValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"AclAnnotationValidation ($validationMode)" should {

    "accept @Acl on class and method of HttpEndpoint" in {
      assertValid("valid/ValidHttpEndpointWithAcl.java")
    }

    "accept @Acl on class and method of GrpcEndpoint" in {
      assertValid("valid/ValidGrpcEndpointWithAcl.java")
    }

    "accept @Acl on class and method of McpEndpoint" in {
      assertValid("valid/ValidMcpEndpointWithAcl.java")
    }

    "accept @Acl on class of Consumer with @Produce.ServiceStream" in {
      assertValid("valid/ValidConsumerWithServiceStreamAndAcl.java")
    }

    "accept @Acl on method of Consumer with @Produce.ServiceStream" in {
      assertValid("valid/ValidConsumerWithServiceStreamAndMethodAcl.java")
    }

    "reject @Acl on a Consumer without @Produce.ServiceStream" in {
      assertInvalid(
        "invalid/AclOnConsumer.java",
        "@Acl annotation is only allowed on classes annotated with @HttpEndpoint, @GrpcEndpoint, @McpEndpoint or @Produce.ServiceStream")
    }

    "reject @Acl on a non-endpoint component class" in {
      assertInvalid(
        "invalid/AclOnComponent.java",
        "@Acl annotation is only allowed on classes annotated with @HttpEndpoint, @GrpcEndpoint, @McpEndpoint or @Produce.ServiceStream")
    }

    "reject @Acl on a method of a non-endpoint component" in {
      assertInvalid(
        "invalid/AclOnComponentMethod.java",
        "@Acl annotation is only allowed on methods of classes annotated with @HttpEndpoint, @GrpcEndpoint, @McpEndpoint or @Produce.ServiceStream")
    }

    "accept @JWT on class and method of HttpEndpoint" in {
      assertValid("valid/ValidHttpEndpointWithJwt.java")
    }

    "accept @JWT on class and method of GrpcEndpoint" in {
      assertValid("valid/ValidGrpcEndpointWithJwt.java")
    }

    "reject @JWT on a non-endpoint component class" in {
      assertInvalid(
        "invalid/JwtOnComponent.java",
        "@JWT annotation is only allowed on classes annotated with @HttpEndpoint, @GrpcEndpoint or @McpEndpoint")
    }

    "reject @JWT on a method of a non-endpoint component" in {
      assertInvalid(
        "invalid/JwtOnComponentMethod.java",
        "@JWT annotation is only allowed on methods of classes annotated with @HttpEndpoint, @GrpcEndpoint or @McpEndpoint")
    }
  }
}
