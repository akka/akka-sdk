/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeComponentValidationSpec extends AbstractComponentValidationSpec(CompileTimeValidation)
class RuntimeComponentValidationSpec extends AbstractComponentValidationSpec(RuntimeValidation)

abstract class AbstractComponentValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with Matchers
    with CompilationTestSupport {

  s"ComponentValidationProcessor ($validationMode)" should {

    // Valid components
    "accept valid public components" in {
      assertValid("valid/ValidPublicComponent.java")
    }

    "accept another valid public component" in {
      assertValid("valid/AnotherValidComponent.java")
    }

    "accept valid component with deprecated @ComponentId" in {
      assertValid("valid/ValidDeprecatedComponentId.java")
    }

    // Public modifier validation
    "reject non-public component" in {
      assertInvalid(
        "invalid/NonPublicComponent.java",
        "NonPublicComponent is not marked with `public` modifier",
        "Components must be public")
    }

    "reject package-private component" in {
      assertInvalid(
        "invalid/PackagePrivateComponent.java",
        "PackagePrivateComponent is not marked with `public` modifier")
    }

    // @Component id validation
    "reject component with empty @Component id" in {
      assertInvalid("invalid/EmptyComponentId.java", "@Component id is empty, must be a non-empty string")
    }

    "reject component with blank @Component id" in {
      assertInvalid("invalid/BlankComponentId.java", "@Component id is empty, must be a non-empty string")
    }

    "reject component with pipe character in @Component id" in {
      assertInvalid("invalid/ComponentIdWithPipe.java", "@Component id must not contain the pipe character")
    }

    // Deprecated @ComponentId validation
    "reject component with empty deprecated @ComponentId" in {
      assertInvalid("invalid/EmptyDeprecatedComponentId.java", "@ComponentId is empty, must be a non-empty string")
    }

    "reject component with blank deprecated @ComponentId" in {
      assertInvalid("invalid/BlankDeprecatedComponentId.java", "@ComponentId is empty, must be a non-empty string")
    }

    "reject component with pipe character in deprecated @ComponentId" in {
      assertInvalid("invalid/DeprecatedComponentIdWithPipe.java", "@ComponentId must not contain the pipe character")
    }

    // Annotation conflicts
    "reject component with both @Component and @ComponentId annotations" in {
      assertInvalid(
        "invalid/BothComponentAndComponentId.java",
        "BothComponentAndComponentId",
        "both @Component and",
        "deprecated @ComponentId")
    }

    // Multiple errors - verify all errors are reported together
    "report all validation errors for a component with multiple issues" in {
      // This test needs special handling to check the error count
      val result = compileTestSource("invalid/MultipleErrorsComponent.java")
      assertCompilationFailure(
        result,
        "MultipleErrorsComponent",
        "not marked with `public` modifier",
        "@Component id is empty")

      // Verify that multiple errors are in the diagnostic list
      val errors = result.diagnostics.filter(_.getKind == javax.tools.Diagnostic.Kind.ERROR)
      errors.length should be >= 2

      // Note: Runtime validation does not currently include componentMustBePublic check,
      // so we only verify that it catches the @Component id error (which is validated)
      tryCompileTestSourceForRuntime("invalid/MultipleErrorsComponent.java").foreach { runtimeResult =>
        val clazz = loadCompiledClass(runtimeResult, "com.example.MultipleErrorsComponent")
        assertRuntimeValidationFailure(clazz, "MultipleErrorsComponent", "@Component id is empty")
      }
    }
  }
}
