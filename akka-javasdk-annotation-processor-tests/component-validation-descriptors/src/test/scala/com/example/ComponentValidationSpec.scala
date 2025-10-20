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
        "NonPublicComponent",
        "not marked with `public` modifier",
        "Components must be public")
    }

    "reject package-private component" in {
      val result = compileTestSource("invalid/PackagePrivateComponent.java")
      assertCompilationFailure(result, "PackagePrivateComponent", "not marked with `public` modifier")
    }

    // @Component id validation
    "reject component with empty @Component id" in {
      val result = compileTestSource("invalid/EmptyComponentId.java")
      assertCompilationFailure(result, "EmptyComponentId", "@Component id is empty")
    }

    "reject component with blank @Component id" in {
      val result = compileTestSource("invalid/BlankComponentId.java")
      assertCompilationFailure(result, "BlankComponentId", "@Component id is empty")
    }

    "reject component with pipe character in @Component id" in {
      val result = compileTestSource("invalid/ComponentIdWithPipe.java")
      assertCompilationFailure(result, "ComponentIdWithPipe", "pipe character")
    }

    // Deprecated @ComponentId validation
    "reject component with empty deprecated @ComponentId" in {
      val result = compileTestSource("invalid/EmptyDeprecatedComponentId.java")
      assertCompilationFailure(result, "EmptyDeprecatedComponentId", "@ComponentId name is empty")
    }

    "reject component with blank deprecated @ComponentId" in {
      val result = compileTestSource("invalid/BlankDeprecatedComponentId.java")
      assertCompilationFailure(result, "BlankDeprecatedComponentId", "@ComponentId name is empty")
    }

    "reject component with pipe character in deprecated @ComponentId" in {
      val result = compileTestSource("invalid/DeprecatedComponentIdWithPipe.java")
      assertCompilationFailure(result, "DeprecatedComponentIdWithPipe", "pipe character")
    }

    // Annotation conflicts
    "reject component with both @Component and @ComponentId annotations" in {
      val result = compileTestSource("invalid/BothComponentAndComponentId.java")
      assertCompilationFailure(result, "BothComponentAndComponentId", "both @Component and", "deprecated @ComponentId")
    }
  }
}
