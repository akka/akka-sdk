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

    "reject @Acl on a non-endpoint component class" in {
      assertInvalid(
        "invalid/AclOnComponent.java",
        "@Acl annotation is only allowed on classes annotated with @HttpEndpoint, @GrpcEndpoint or @McpEndpoint")
    }

    "reject @Acl on a method of a non-endpoint component" in {
      assertInvalid(
        "invalid/AclOnComponentMethod.java",
        "@Acl annotation is only allowed on methods of classes annotated with @HttpEndpoint, @GrpcEndpoint or @McpEndpoint")
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
