/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeA2aEndpointValidationSpec extends AbstractA2aEndpointValidationSpec(CompileTimeValidation) {

  "ComponentValidationProcessor" should {

    "reject A2A endpoints on the same path, also when written differently" in {
      assertInvalid(
        "invalid/DuplicateA2aEndpointPaths.java",
        "On 'com.example.DuplicateA2aEndpointPaths.First': @A2aEndpoint path [/a2a/claims] is not unique. It is also used by [com.example.DuplicateA2aEndpointPaths.Second].",
        "On 'com.example.DuplicateA2aEndpointPaths.Second': @A2aEndpoint path [/a2a/claims] is not unique. It is also used by [com.example.DuplicateA2aEndpointPaths.First].",
        "On 'com.example.DuplicateA2aEndpointPaths.DefaultRoot': @A2aEndpoint path [/] is not unique. It is also used by [com.example.DuplicateA2aEndpointPaths.EmptyRoot].",
        "On 'com.example.DuplicateA2aEndpointPaths.EmptyRoot': @A2aEndpoint path [/] is not unique. It is also used by [com.example.DuplicateA2aEndpointPaths.DefaultRoot].")
    }
  }
}

class RuntimeA2aEndpointValidationSpec extends AbstractA2aEndpointValidationSpec(RuntimeValidation)

abstract class AbstractA2aEndpointValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"A2aEndpoint validation ($validationMode)" should {

    "accept an endpoint with @Acl and @JWT" in {
      assertValid("valid/ValidA2aEndpoint.java")
    }

    "reject an endpoint that does not extend AbstractA2aEndpoint" in {
      assertInvalid(
        "invalid/A2aEndpointWithoutBaseClass.java",
        "On 'com.example.A2aEndpointWithoutBaseClass': A class annotated with @A2aEndpoint must extend akka.javasdk.a2a.AbstractA2aEndpoint.")
    }
  }
}
