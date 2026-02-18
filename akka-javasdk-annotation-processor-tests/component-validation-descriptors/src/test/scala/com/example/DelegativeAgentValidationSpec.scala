/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class AgentTeamAgentValidationSpec extends AnyWordSpec with CompilationTestSupport {

  "AgentTeam Agent validation" should {

    "accept valid AgentTeam Agent with no command handlers" in {
      val result = compileTestSource("valid/ValidAgentTeamAgent.java")
      assertCompilationSuccess(result)
    }

    "reject AgentTeam Agent with a command handler" in {
      val result = compileTestSource("invalid/AgentTeamAgentWithCommandHandler.java")
      assertCompilationFailure(
        result,
        "implements AgentTeam and has 1 command handler(s)",
        "AgentTeam agents must not have public methods returning Agent.Effect or Agent.StreamEffect")
    }
  }
}
