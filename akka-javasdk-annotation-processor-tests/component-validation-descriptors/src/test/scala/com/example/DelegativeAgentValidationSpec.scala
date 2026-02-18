/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class DelegativeAgentValidationSpec extends AnyWordSpec with CompilationTestSupport {

  "Delegative Agent validation" should {

    "accept valid Delegative Agent with no command handlers" in {
      val result = compileTestSource("valid/ValidDelegativeAgent.java")
      assertCompilationSuccess(result)
    }

    "reject Delegative Agent with a command handler" in {
      val result = compileTestSource("invalid/DelegativeAgentWithCommandHandler.java")
      assertCompilationFailure(
        result,
        "implements Delegative and has 1 command handler(s)",
        "Delegative agents must not have public methods returning Agent.Effect or Agent.StreamEffect")
    }
  }
}
