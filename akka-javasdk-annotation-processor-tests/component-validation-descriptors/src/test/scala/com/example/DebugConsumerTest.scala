/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class DebugConsumerTest extends AnyWordSpec with CompilationTestSupport {

  "Debug" should {
    "print errors for delete handler test" in {
      val result = compileTestSource("valid/ValidConsumerWithDeleteHandler.java")

      println("=== COMPILATION RESULT ===")
      println(s"Success: ${result.success}")
      println("=== DIAGNOSTICS ===")
      result.diagnostics.foreach { diag =>
        println(s"${diag.getKind}: ${diag.getMessage(null)}")
      }
      println("=== END ===")

      // This will fail, but we just want to see the output
      result.success shouldBe true
    }
  }
}
