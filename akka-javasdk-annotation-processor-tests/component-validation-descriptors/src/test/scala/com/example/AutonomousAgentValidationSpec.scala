/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeAutonomousAgentValidationSpec extends AbstractAutonomousAgentValidationSpec(CompileTimeValidation)
class RuntimeAutonomousAgentValidationSpec extends AbstractAutonomousAgentValidationSpec(RuntimeValidation)

abstract class AbstractAutonomousAgentValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"AutonomousAgent validation ($validationMode)" should {

    // Valid autonomous agents
    "accept valid AutonomousAgent with strategy" in {
      assertValid("valid/ValidAutonomousAgent.java")
    }

    "accept AutonomousAgent with @FunctionTool methods" in {
      assertValid("valid/ValidAutonomousAgentWithFunctionTool.java")
    }

    // Invalid autonomous agents
    "reject AutonomousAgent with command handler" in {
      assertInvalid(
        "invalid/AutonomousAgentWithCommandHandler.java",
        "AutonomousAgent must not define command handler methods")
    }
  }
}
