/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class ComponentValidationSpec extends AnyWordSpec with CompilationTestSupport {

  "ComponentValidationProcessor" should {

    // Valid components
    "accept valid public components" in {
      val result = compileTestSource("valid/ValidPublicComponent.java")
      assertCompilationSuccess(result)
    }

    "accept another valid public component" in {
      val result = compileTestSource("valid/AnotherValidComponent.java")
      assertCompilationSuccess(result)
    }

    "accept valid component with deprecated @ComponentId" in {
      val result = compileTestSource("valid/ValidDeprecatedComponentId.java")
      assertCompilationSuccess(result)
    }

    // Public modifier validation
    "reject non-public component" in {
      val result = compileTestSource("invalid/NonPublicComponent.java")
      assertCompilationFailure(
        result,
        "NonPublicComponent is not marked with `public` modifier",
        "Components must be public")
    }

    "reject package-private component" in {
      val result = compileTestSource("invalid/PackagePrivateComponent.java")
      assertCompilationFailure(result, "PackagePrivateComponent is not marked with `public` modifier")
    }

    // @Component id validation
    "reject component with empty @Component id" in {
      val result = compileTestSource("invalid/EmptyComponentId.java")
      assertCompilationFailure(result, "@Component id is empty, must be a non-empty string")
    }

    "reject component with blank @Component id" in {
      val result = compileTestSource("invalid/BlankComponentId.java")
      assertCompilationFailure(result, "@Component id is empty, must be a non-empty string")
    }

    "reject component with pipe character in @Component id" in {
      val result = compileTestSource("invalid/ComponentIdWithPipe.java")
      assertCompilationFailure(result, "@Component id must not contain the pipe character")
    }

    // Deprecated @ComponentId validation
    "reject component with empty deprecated @ComponentId" in {
      val result = compileTestSource("invalid/EmptyDeprecatedComponentId.java")
      assertCompilationFailure(result, "@ComponentId is empty, must be a non-empty string")
    }

    "reject component with blank deprecated @ComponentId" in {
      val result = compileTestSource("invalid/BlankDeprecatedComponentId.java")
      assertCompilationFailure(result, "@ComponentId is empty, must be a non-empty string")
    }

    "reject component with pipe character in deprecated @ComponentId" in {
      val result = compileTestSource("invalid/DeprecatedComponentIdWithPipe.java")
      assertCompilationFailure(result, "@ComponentId must not contain the pipe character")
    }

    // Annotation conflicts
    "reject component with both @Component and @ComponentId annotations" in {
      val result = compileTestSource("invalid/BothComponentAndComponentId.java")
      assertCompilationFailure(result, "BothComponentAndComponentId", "both @Component and", "deprecated @ComponentId")
    }

    // Multiple errors - verify all errors are reported together
    "report all validation errors for a component with multiple issues" in {
      val result = compileTestSource("invalid/MultipleErrorsComponent.java")
      assertCompilationFailure(
        result,
        "MultipleErrorsComponent",
        "not marked with `public` modifier",
        "@Component id is empty")

      // Verify that multiple errors are in the diagnostic list
      val errors = result.diagnostics.filter(_.getKind == javax.tools.Diagnostic.Kind.ERROR)
      errors.length should be >= 2
    }
  }
}
